#!/Users/yoshi/venv/bin/python3
"""
meds_check — 飲み忘れリマインド（yocron every 5m で実行）

スケジュールモデルはスマホアプリ(MedicineCaseMob)準拠（docs/SYSTEM_SPEC.md §3）:

- 枠: 朝/昼/夜（開始/終了・連動・有効トグル）。服用の帰属は時刻ベース判定
  （押下時刻が [start, end) に属する枠・活動時間外の押下はどの枠にも帰属しない）
- 攻め（推奨時刻通知）: 推奨時刻（最大3つ・枠とは独立）に到達し、その推奨時刻が
  有効枠の時間帯内で、その枠が未服用なら通知。推奨時刻ごとに1日1回
- 守り（追い通知）: 終了時刻を過ぎた有効枠のうち最も遅い1枠のみ対象（直近枠ルール）。
  未服用なら初回は即通知、以後 remind.interval_min ごとに再通知。
  枠終了後の押下でも服用済み扱いにして通知を止める（寛容側）

状態は remind-state.json に置き、二重通知を防ぐ。
テスト: MEDS_FAKE_TIME=YYYY-MM-DDTHH:MM で「今」を差し替え（タイトルに (テスト) が付く）。
"""

import json
import os
import subprocess
from datetime import datetime, timedelta

HOME = os.path.expanduser("~")
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SETTING_PATH = os.path.join(BASE_DIR, "setting.json")
DATA_DIR = os.path.join(HOME, "www-portal/data/meds")
CONFIG_PATH = os.path.join(DATA_DIR, "config.json")
HISTORY_PATH = os.path.join(DATA_DIR, "history.jsonl")
STATE_PATH = os.path.join(DATA_DIR, "remind-state.json")

DEFAULT_CONFIG = {
    "slots": [
        {"name": "朝", "start": "07:00", "end": "11:00", "enabled": True},
        {"name": "昼", "start": "11:00", "end": "17:00", "enabled": True},
        {"name": "夜", "start": "17:00", "end": "23:00", "enabled": True},
    ],
    "recommend": ["09:00", "13:00", "20:00"],
    "remind": {"interval_min": 60},
    "switches": ["スイッチ1", "スイッチ2", "スイッチ3", "", "", ""],
}


def log(*args):
    print(datetime.now().strftime("%Y-%m-%d %H:%M:%S"), *args, flush=True)


def now():
    fake = os.environ.get("MEDS_FAKE_TIME")
    if fake:
        return datetime.fromisoformat(fake)
    return datetime.now()


def load_json(path, default):
    try:
        with open(path) as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return default


def load_config():
    merged = json.loads(json.dumps(DEFAULT_CONFIG))
    user = load_json(CONFIG_PATH, {})
    for k in merged:
        if k in user:
            merged[k] = user[k]
    # 形状ガイド（旧形式・配置ミスで daemon/check が死なないように）
    if not isinstance(merged["slots"], list) or not merged["slots"] or \
            "start" not in (merged["slots"][0] or {}):
        merged["slots"] = json.loads(json.dumps(DEFAULT_CONFIG["slots"]))
    if not isinstance(merged.get("recommend"), list):
        merged["recommend"] = list(DEFAULT_CONFIG["recommend"])
    if not isinstance(merged.get("remind"), dict) or "interval_min" not in merged.get("remind", {}):
        merged["remind"] = dict(DEFAULT_CONFIG["remind"])
    if not isinstance(merged.get("switches"), list):
        merged["switches"] = list(DEFAULT_CONFIG["switches"])
    return merged


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


def todays_intakes(today):
    out = []
    try:
        with open(HISTORY_PATH) as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    e = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if datetime.fromtimestamp(e.get("ts", 0)).date() == today:
                    out.append(e["ts"])
    except FileNotFoundError:
        pass
    return sorted(out)


def dt_at(today, hhmm):
    h, m = hhmm.split(":")
    return datetime.combine(today, datetime.min.time()).replace(hour=int(h), minute=int(m))


def slot_of(slots, t, today):
    """時刻 t が [start,end) に属する最初の有効枠。無ければ None（活動時間外）"""
    for s in slots:
        if not s.get("enabled", True):
            continue
        if dt_at(today, s["start"]) <= t < dt_at(today, s["end"]):
            return s
    return None


def slot_taken_ts(intakes, s, today):
    """枠 [start,end) 内の最初の服用 ts。無ければ None"""
    a = dt_at(today, s["start"]).timestamp()
    b = dt_at(today, s["end"]).timestamp()
    for ts in intakes:
        if a <= ts < b:
            return ts
    return None


def main():
    setting = load_json(SETTING_PATH, {"ntfy_url": "https://ntfy.sh/claude-code-notice215"})
    cfg = load_config()
    n = now()
    today = n.date()
    fake = bool(os.environ.get("MEDS_FAKE_TIME"))
    tag = " (テスト)" if fake else ""

    intakes = todays_intakes(today)
    interval = int(cfg["remind"].get("interval_min", 60))

    state = load_json(STATE_PATH, {})
    if state.get("date") != today.isoformat():
        state = {"date": today.isoformat(), "rec": {}, "slots": {}}
    rec_state = state.setdefault("rec", {})
    slots_state = state.setdefault("slots", {})

    # ---- 攻め: 推奨時刻通知（推奨時刻ごとに1日1回・有効枠内のみ）----
    for i, rec in enumerate(cfg["recommend"][:3]):
        rec_dt = dt_at(today, rec)
        slot = slot_of(cfg["slots"], rec_dt, today)
        if slot is None:
            continue   # 有効枠の範囲外に設定された推奨時刻は無視
        if rec_state.get(str(i), {}).get("notified"):
            continue
        if n < rec_dt:
            continue
        if slot_taken_ts(intakes, slot, today) is not None:
            continue   # 服用済みなら通知もフラグも不要
        title = "💊 推奨時刻です%s: %sの薬はまだ記録がありません" % (tag, slot["name"])
        body = "%s（推奨 %s・枠 %s-%s）。飲んだらスイッチを押してください。" % (
            slot["name"], rec, slot["start"], slot["end"])
        send_ntfy(setting, title, body, high=True)
        rec_state[str(i)] = {"notified": n.isoformat(timespec="seconds"), "slot": slot["name"]}
        log("推奨時刻通知:", slot["name"], rec)

    # ---- 守り: 追い通知（直近枠ルール: 終了を過ぎた有効枠のうち最も遅い1枠のみ）----
    ended = [s for s in cfg["slots"] if s.get("enabled", True) and dt_at(today, s["end"]) <= n]
    target = max(ended, key=lambda s: dt_at(today, s["end"])) if ended else None
    for s in cfg["slots"]:
        if target is None or s["name"] != target["name"]:
            slots_state.pop(s["name"], None)   # 対象外になった枠の通知状態を掃除
    if target is not None:
        a = dt_at(today, target["start"]).timestamp()
        # 服用済み判定は寛容側: 枠内は厳密、枠終了後の押下でも「飲んだ」扱いにして通知を止める
        taken = any(ts >= a for ts in intakes)
        if taken:
            slots_state.pop(target["name"], None)
        else:
            st = slots_state.get(target["name"], {})
            last_sent = st.get("last_sent")
            if last_sent:
                due = (n - datetime.fromisoformat(last_sent)) >= timedelta(minutes=interval)
            else:
                due = True   # 初回はインターバル制限を無視して即通知
            if due:
                title = "💊 %sの薬を飲んでいません%s" % (target["name"], tag)
                body = "枠 %s-%s を過ぎました。%d分間隔で再通知します。" % (
                    target["start"], target["end"], interval)
                send_ntfy(setting, title, body, high=True)
                slots_state[target["name"]] = {"last_sent": n.isoformat(timespec="seconds")}
                log("追い通知:", target["name"], "count=", st.get("count", 0) + 1)

    tmp = STATE_PATH + ".tmp"
    with open(tmp, "w") as f:
        json.dump(state, f, ensure_ascii=False, indent=1)
        f.write("\n")
    os.replace(tmp, STATE_PATH)


if __name__ == "__main__":
    main()
