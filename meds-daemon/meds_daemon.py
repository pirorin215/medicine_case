#!/Users/yoshi/venv/bin/python3
"""
meds_daemon — Medicine Case Pro Micro (USB シリアル) の受信・記録・通知 常駐daemon

yocron keepalive (alive_port) 配下で常駐し、死亡時は keepalive が respawn する。

データ（単一真実は ~/www-portal/data/meds/ 配下・git管理外）:
  config.json    スケジュールの正（ポータル api.php と meds_check.py が読む）
  status.json    デバイス/daemon の現在状態（ポータルが読む・daemon が書く）
  history.jsonl  服薬イベントの追記型ログ

デバイスはマイクロスイッチ押下で INTAKE を送る（v2.1・しきい値設定なし）。
config.json を daemon は読まない（MCU への設定反映も存在しない）。
"""

import json
import os
import signal
import socket
import subprocess
import sys
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
    # スイッチ名は Mac 側で管理・必ず 6要素のリスト（idx=D4=0...D9=5・未配線は無視される）。
    # PHP 側が数値キー連想配列を JSON リスト化するため dict は使わない（2026-10-07 クラッシュ教訓）
    "switches": ["スイッチ1", "スイッチ2", "スイッチ3", "", "", ""],
}

# HB がこの秒数途絶えたら接続を張り直し（デバイスは60秒HB・待機中は送信なし）
LINK_STALE_SEC = 90


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
    # 型の歪みに耐える（配置形式違いで daemon が死なないように）
    if not isinstance(merged.get("switches"), list):
        merged["switches"] = list(DEFAULT_CONFIG["switches"])
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


class DeviceState:
    """MCU から観測した状態（status.json の device セクション）"""

    def __init__(self):
        self.name = None
        self.fw = None
        self.last_seen = None
        self.last_intake_by_switch = {}   # idx -> ts（dedup と status 表示に使用）
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
            "last_intake_ts": max(self.last_intake_by_switch.values()) if self.last_intake_by_switch else None,
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
        # CONFIG name=medcase1 v=2.1.0
        kv = dict(p.split("=", 1) for p in parts[1:] if "=" in p)
        dev.name = kv.get("name", dev.name)
        dev.fw = kv.get("v", dev.fw)

    elif tag == "HB":
        dev.last_seen = time.time()

    elif tag == "INTAKE":
        # INTAKE <idx> <age_ms>（v2.2 互換: INTAKE <age> は idx=0 扱い）
        dev.last_seen = time.time()
        try:
            if len(parts) >= 3:
                idx = int(parts[1])
                age_ms = int(parts[2])
            else:
                idx = 0
                age_ms = int(parts[1])
        except (IndexError, ValueError):
            return
        ts = time.time() - age_ms / 1000.0

        # 二重受信 insurance（同一スイッチ・5秒以内は無視・別スイッチは有効）
        last = dev.last_intake_by_switch.get(idx)
        if last is not None and abs(ts - last) < 5:
            return
        dev.last_intake_by_switch[idx] = ts

        cfg = load_config()
        sw_names = cfg.get("switches") or []
        sw_name = sw_names[idx] if (isinstance(sw_names, list) and idx < len(sw_names) and sw_names[idx]) \
            else "スイッチ%d" % (idx + 1)
        entry = {
            "ts": int(ts),
            "iso": iso(ts),
            "device": dev.name or "unknown",
            "switch": idx,
            "switch_name": sw_name,
            "source": "switch",
        }
        append_history(entry)
        title = "💊 %sを記録 %s" % (sw_name, datetime.fromtimestamp(ts).strftime("%H:%M"))
        body = "%s %s %s" % (entry["device"], sw_name, entry["iso"])
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
                        # 1行の処理で落ちても daemon は死なない（v2.3 初日クラッシュの教訓）
                        try:
                            handle_line(line, setting, dev, lambda s: ser.write((s + "\n").encode()))
                        except Exception as e:
                            log("行処理エラー(無視):", repr(e), "| line:", line)

            # リンク死活（HB/T が途絶えたら張り直し）
            if dev.last_seen is not None and now - dev.last_seen > LINK_STALE_SEC:
                raise serial.SerialException("link stale (HB/T timeout)")

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
    # SIGTERM（yocron timeout 到達・手動 stop）でも run_serial の finally を
    # 通らせ、status.json を connected=false にして終わる（デフォルトハンドラは
    # finally をスキップして「接続中」のまま残留する）
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))
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
        except (KeyboardInterrupt, SystemExit):
            break


if __name__ == "__main__":
    main()
