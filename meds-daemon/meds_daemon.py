#!/Users/yoshi/venv/bin/python3
"""
meds_daemon — Medicine Case Pro Micro (USB シリアル) の受信・記録・通知 常駐daemon

yocron keepalive (alive_port) 配下で常駐し、死亡時は keepalive が respawn する。

データ（単一真実は ~/www-portal/data/meds/ 配下・git管理外）:
  config.json    スケジュール・しきい値の正（ポータル api.php が書く・daemon は読む）
  status.json    デバイス/daemon の現在状態（ポータルが読む・daemon が書く）
  history.jsonl  服薬イベントの追記型ログ

daemon は config.json の device セクションと MCU の実設定の差分を見て
SET:angle / SET:cooldown を送るため、しきい値変更はポータルから行える。
"""

import json
import os
import socket
import subprocess
import threading
import time
from datetime import datetime

import serial

HOME = os.path.expanduser("~")
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SETTING_PATH = os.path.join(BASE_DIR, "setting.json")
DATA_DIR = os.path.join(HOME, "www-portal/data/meds")
CONFIG_PATH = os.path.join(DATA_DIR, "config.json")
STATUS_PATH = os.path.join(DATA_DIR, "status.json")
HISTORY_PATH = os.path.join(DATA_DIR, "history.jsonl")

DEFAULT_CONFIG = {
    "slots": [
        {"name": "朝", "time": "08:00", "enabled": True},
        {"name": "昼", "time": "12:30", "enabled": True},
        {"name": "夜", "time": "22:00", "enabled": True},
    ],
    "remind": {"repeat_min": 30, "window_min": 120},
    "device": {"angle": 70.0, "cooldown_ms": 30000},
}

# HB/T がこの秒数途絶えたら接続を張り直す
LINK_STALE_SEC = 30


def log(*args):
    print(datetime.now().strftime("%Y-%m-%d %H:%M:%S"), *args, flush=True)


def iso(ts=None):
    return datetime.fromtimestamp(ts if ts is not None else time.time()).isoformat(timespec="seconds")


def load_setting():
    with open(SETTING_PATH) as f:
        return json.load(f)


def load_config():
    merged = json.loads(json.dumps(DEFAULT_CONFIG))
    try:
        with open(CONFIG_PATH) as f:
            user = json.load(f)
        for k in merged:
            if k in user:
                merged[k] = user[k]
    except (FileNotFoundError, json.JSONDecodeError) as e:
        log("config load:", e, "→ DEFAULT 使用")
    return merged


def write_json_atomic(path, obj):
    tmp = path + ".tmp"
    with open(tmp, "w") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
        f.write("\n")
    os.replace(tmp, path)


def append_history(entry):
    with open(HISTORY_PATH, "a") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")


def send_ntfy(setting, title, body, high=False):
    cmd = ["/usr/bin/curl", "-s", "-m", "10",
           "-H", "Title: " + title,
           "-H", "Tags: pill"]
    if high:
        cmd += ["-H", "Priority: high"]
    cmd += ["-d", body, setting["ntfy_url"]]
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
        if r.returncode != 0:
            log("ntfy失敗:", r.stderr[:200])
    except subprocess.TimeoutExpired:
        log("ntfyタイムアウト")


def slot_for_ts(slots, ts):
    """時刻帯の枠名。当日の最後の「枠開始 <= t」の枠。どの枠より前なら None。"""
    t = datetime.fromtimestamp(ts)
    best = None
    for s in slots:
        try:
            h, m = s["time"].split(":")
        except (KeyError, ValueError):
            continue
        slot_dt = t.replace(hour=int(h), minute=int(m), second=0, microsecond=0)
        if slot_dt <= t:
            best = s
    return best["name"] if best else None


class DeviceState:
    """MCU から観測した状態（status.json の device セクション）"""

    def __init__(self):
        self.name = None
        self.fw = None
        self.angle = None
        self.cooldown_ms = None
        self.pitch = None
        self.roll = None
        self.state = None
        self.last_seen = None
        self.last_intake_ts = None
        self.offline_alerted = False

    def as_dict(self, setting, connected):
        online = connected and self.last_seen is not None and \
            (time.time() - self.last_seen) < LINK_STALE_SEC
        return {
            "name": self.name,
            "fw": self.fw,
            "connected": connected,
            "online": online,
            "last_seen": iso(self.last_seen) if self.last_seen else None,
            "pitch": self.pitch,
            "roll": self.roll,
            "state": self.state,
            "angle": self.angle,
            "cooldown_ms": self.cooldown_ms,
            "last_intake_ts": self.last_intake_ts,
            "serial_port": setting["serial_port"],
        }


def serve_alive(port):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("127.0.0.1", port))
    s.listen(8)
    while True:
        conn, _ = s.accept()
        conn.close()


def sync_device_config(cfg, dev, write_line):
    """config.json の device 設定と MCU の差分を SET で反映"""
    want = cfg.get("device", {})
    if dev.angle is not None and want.get("angle") is not None and \
            abs(float(want["angle"]) - dev.angle) > 0.05:
        write_line("SET:angle:%s" % want["angle"])
        log("SET:angle:%s 送信（MCU=%s）" % (want["angle"], dev.angle))
    if dev.cooldown_ms is not None and want.get("cooldown_ms") is not None and \
            int(want["cooldown_ms"]) != int(dev.cooldown_ms):
        write_line("SET:cooldown:%s" % int(want["cooldown_ms"]))
        log("SET:cooldown:%s 送信（MCU=%s）" % (want["cooldown_ms"], dev.cooldown_ms))


def handle_line(line, setting, dev, write_line):
    parts = line.split()
    tag = parts[0] if parts else ""

    if tag == "HELLO":
        # HELLO medcase <name> v<x.y.z>
        dev.name = parts[2] if len(parts) > 2 else None
        dev.fw = parts[3] if len(parts) > 3 else None
        dev.last_seen = time.time()
        dev.offline_alerted = False
        log("接続:", line)

    elif tag == "CONFIG":
        # CONFIG angle=70.0 cooldown=30000 name=medcase1 v=2.0.1
        kv = dict(p.split("=", 1) for p in parts[1:] if "=" in p)
        try:
            dev.angle = float(kv.get("angle"))
            dev.cooldown_ms = int(float(kv.get("cooldown")))
        except (TypeError, ValueError):
            pass
        dev.name = kv.get("name", dev.name)
        dev.fw = kv.get("v", dev.fw)
        sync_device_config(load_config(), dev, write_line)

    elif tag == "HB":
        dev.last_seen = time.time()

    elif tag == "T":
        dev.last_seen = time.time()
        try:
            dev.pitch = float(parts[1])
            dev.roll = float(parts[2])
            dev.state = parts[3]
        except (IndexError, ValueError):
            pass

    elif tag == "INTAKE":
        # INTAKE <maxChange> <age_ms>
        dev.last_seen = time.time()
        try:
            max_change = float(parts[1])
            age_ms = int(parts[2])
        except (IndexError, ValueError):
            return
        ts = time.time() - age_ms / 1000.0

        # 軽い二重受信 insurance（同一max・5秒以内は無視）
        if dev.last_intake_ts is not None and abs(ts - dev.last_intake_ts) < 5:
            return
        dev.last_intake_ts = ts

        cfg = load_config()
        entry = {
            "ts": int(ts),
            "iso": iso(ts),
            "device": dev.name or "unknown",
            "max_change": max_change,
            "source": "serial",
        }
        append_history(entry)
        slot = slot_for_ts(cfg.get("slots", []), ts)
        slot_label = slot + "枠" if slot else ""
        title = "💊 服薬を記録 %s%s" % (
            datetime.fromtimestamp(ts).strftime("%H:%M"), ("・" + slot_label) if slot else "")
        body = "%s (最大変化 %.1f°) %s" % (entry["device"], max_change, entry["iso"])
        send_ntfy(setting, title, body)
        log("INTAKE記録:", entry)

    elif tag.startswith("OK:") or tag.startswith("ERR:"):
        log("MCU応答:", line)

    else:
        if tag and not tag.startswith("["):
            log("未知の行:", line)


def run_serial(setting, dev):
    """シリアル接続を開いて行を読み続ける。切断時は例外を投げて呼び元に戻る"""
    port = setting["serial_port"]
    ser = serial.Serial(port, 115200, timeout=5)
    log("ポートオープン:", port)
    buf = b""
    last_status_write = 0.0
    last_config_mtime = None

    try:
        while True:
            chunk = ser.read(256)
            now = time.time()

            if chunk:
                buf += chunk
                while b"\n" in buf:
                    raw, buf = buf.split(b"\n", 1)
                    line = raw.decode("utf-8", "replace").strip()
                    if line:
                        handle_line(line, setting, dev, lambda s: ser.write((s + "\n").encode()))

            # リンク死活（HB/T が途絶えたら張り直し）
            if dev.last_seen is not None and now - dev.last_seen > LINK_STALE_SEC:
                raise serial.SerialException("link stale (HB/T timeout)")

            # config.json 変更検知 → MCU へ反映
            try:
                mtime = os.path.getmtime(CONFIG_PATH)
                if last_config_mtime is not None and mtime != last_config_mtime:
                    sync_device_config(load_config(), dev, lambda s: ser.write((s + "\n").encode()))
                last_config_mtime = mtime
            except FileNotFoundError:
                pass

            # オフライン監視（接続中に HB が止まったら…は上で張り直すので、
            # ここでは「USB抜け等でポート自体が開けない」状況を呼び元が扱う）

            if now - last_status_write >= 5:
                last_status_write = now
                write_status(setting, dev, connected=True)
    finally:
        ser.close()
        write_status(setting, dev, connected=False)


def write_status(setting, dev, connected):
    status = {
        "daemon": {
            "pid": os.getpid(),
            "started": DAEMON_STARTED,
            "data_dir": DATA_DIR,
        },
        "device": dev.as_dict(setting, connected),
        "updated": iso(),
    }
    write_json_atomic(STATUS_PATH, status)

    # オフラインアラート（ポートが開けない/無音が続く状態が続いたら1回だけ通知）
    online = status["device"]["online"]
    if not online and dev.last_seen is not None and not dev.offline_alerted:
        if time.time() - dev.last_seen > setting.get("device_offline_alert_sec", 300):
            dev.offline_alerted = True
            send_ntfy(setting, "💊 薬ケースがオフライン",
                      "USB抜け・MCU停止の可能性 (%s)" % setting["serial_port"])


DAEMON_STARTED = iso()


def main():
    setting = load_setting()
    os.makedirs(DATA_DIR, exist_ok=True)
    # history.jsonl 無ければ作る
    if not os.path.exists(HISTORY_PATH):
        open(HISTORY_PATH, "a").close()

    threading.Thread(target=serve_alive, args=(setting["alive_port"],), daemon=True).start()
    log("meds-daemon 起動 port=%s serial=%s" % (setting["alive_port"], setting["serial_port"]))

    dev = DeviceState()
    while True:
        try:
            run_serial(setting, dev)
        except (serial.SerialException, OSError) as e:
            log("serial:", e, "→ 5秒後に再接続")
            write_status(setting, dev, connected=False)
            time.sleep(5)
        except KeyboardInterrupt:
            break


if __name__ == "__main__":
    main()
