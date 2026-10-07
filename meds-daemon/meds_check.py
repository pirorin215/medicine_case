#!/Users/yoshi/venv/bin/python3
"""
meds_check — 飲み忘れリマインド（yocron every 5m で実行）

config.json の各枠について「予定時刻を過ぎたのに history.jsonl に服用記録が無ければ」
ntfy でリマインドする。枠の window_min を過ぎたら諦めて次の枠へ。
再通知は repeat_min 間隔。状態は remind-state.json に置き、二重通知を防ぐ。

服用の枠帰属: 当日の枠を順に見て、[枠開始-240分, 枠開始+window_min) に記録された
未使用の服用をその枠の分として数える（予定時刻前に飲んでも吸收）。

テスト: MEDS_FAKE_TIME=2026-10-08T08:35 のように「今」を差し替えられる
（タイトルに (テスト) が付く）。gomi の GOMI_FAKE_TODAY と同じ方式。
"""

import json
import os
import subprocess
from datetime import datetime, timedelta, date as date_cls

HOME = os.path.expanduser("~")
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SETTING_PATH = os.path.join(BASE_DIR, "setting.json")
DATA_DIR = os.path.join(HOME, "www-portal/data/meds")
CONFIG_PATH = os.path.join(DATA_DIR, "config.json")
HISTORY_PATH = os.path.join(DATA_DIR, "history.jsonl")
STATE_PATH = os.path.join(DATA_DIR, "remind-state.json")

DEFAULT_CONFIG = {
    "slots": [
        {"name": "朝", "time": "08:00", "enabled": True},
        {"name": "昼", "time": "12:30", "enabled": True},
        {"name": "夜", "time": "22:00", "enabled": True},
    ],
    "remind": {"repeat_min": 30, "window_min": 120},
}

# 枠開始の何分前までの服用をその枠に数えるか
GRACE_BEFORE_MIN = 240


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
    """history.jsonl から当日の服用 ts 一覧を返す"""
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


def main():
    setting = load_json(SETTING_PATH, {"ntfy_url": "https://ntfy.sh/claude-code-notice215"})
    cfg = load_config()
    n = now()
    today = n.date()
    fake = bool(os.environ.get("MEDS_FAKE_TIME"))

    # 履歴の枠帰属（枠順に未使用の服用を割り当て）
    intakes = list(todays_intakes(today))
    used = [False] * len(intakes)
    repeat_min = int(cfg.get("remind", {}).get("repeat_min", 30))
    window_min = int(cfg.get("remind", {}).get("window_min", 120))

    state = load_json(STATE_PATH, {})
    if state.get("date") != today.isoformat():
        state = {"date": today.isoformat(), "slots": {}}
    slots_state = state.setdefault("slots", {})

    for slot in cfg.get("slots", []):
        name = slot.get("name", "?")
        if not slot.get("enabled", True):
            continue
        try:
            h, m = slot["time"].split(":")
            slot_dt = datetime.combine(today, datetime.min.time()).replace(hour=int(h), minute=int(m))
        except (KeyError, ValueError):
            log("枠の時刻不正:", slot)
            continue

        window_end = slot_dt + timedelta(minutes=window_min)
        grace_start = slot_dt - timedelta(minutes=GRACE_BEFORE_MIN)

        taken = False
        for i, ts in enumerate(intakes):
            t = datetime.fromtimestamp(ts)
            if not used[i] and grace_start <= t < window_end:
                used[i] = True
                taken = True
                break

        if taken:
            slots_state.pop(name, None)
            continue

        if not (slot_dt <= n < window_end):
            # まだ枠前 or 枠終了済み（終了済みは諦め）
            continue

        st = slots_state.get(name, {})
        last_sent = st.get("last_sent")
        count = int(st.get("count", 0))
        if last_sent:
            last_dt = datetime.fromisoformat(last_sent)
            if (n - last_dt) < timedelta(minutes=repeat_min):
                continue

        title = "💊 %sの薬を飲んでいません%s" % (name, " (テスト)" if fake else "")
        body = "%s 枠（〜%s）。飲んだら薬ケースを傾けて記録してください。" % (
            slot["time"], window_end.strftime("%H:%M"))
        send_ntfy(setting, title, body, high=True)
        log("リマインド送信:", name, "count=%d" % (count + 1))
        slots_state[name] = {
            "last_sent": n.isoformat(timespec="seconds"),
            "count": count + 1,
        }

    tmp = STATE_PATH + ".tmp"
    with open(tmp, "w") as f:
        json.dump(state, f, ensure_ascii=False, indent=1)
        f.write("\n")
    os.replace(tmp, STATE_PATH)


if __name__ == "__main__":
    main()
