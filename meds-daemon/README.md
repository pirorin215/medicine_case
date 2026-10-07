# meds-daemon

Medicine Case Pro Micro（`../medicine_case_promicro/`・マイクロスイッチ版）の
USB シリアルを受ける Mac mini 常駐 daemon。旧BLE版で Android アプリ
（MedicineCaseMob）が担っていた記録・通知・スケジュール管理を Mac 側に引き継ぐ。

## 構成

```
Pro Micro → USB シリアル → meds_daemon.py（常駐・yocron keepalive 配下）
                              ├─ history.jsonl 追記 + ntfy 即時通知（服用記録）
                              ├─ status.json 更新（デバイス状態・ポータルが読む）
                              └─ 設定: setting.json（git管理外・exampleからコピー）
meds_check.py（yocron every 5m）→ 飲み忘れリマインド
```

デバイスはスイッチ押下で `INTAKE <idx> <age_ms>` を送るだけ。しきい値設定は存在しない
（config.json は枠・リマインド・スイッチ名のみで daemon は読まない）。

## スケジュールモデル（スマホアプリ MedicineCaseMob 準拠・SYSTEM_SPEC §3）

- **枠**: 朝/昼/夜（開始/終了時刻・隣接枠と連動・有効トグル）。服用の帰属は時刻ベース判定
  （押下時刻が [start, end) に属する枠。活動時間外の押下は枠に帰属しない）
- **攻め（推奨時刻通知）**: 推奨時刻（最大3つ・枠とは独立）に到達し、その時刻が有効枠内で
  未服用なら通知。推奨時刻ごとに1日1回
- **守り（追い通知）**: 終了を過ぎた有効枠のうち最も遅い1枠のみ対象（直近枠ルール）。
  初回は即・以後 `remind.interval_min`（既定60分）ごと。枠終了後の押下でも通知を止める（寛容側）
- **BLE接続時のチャンス通知のみ非移植**（USB直結では常に接続しているため、
  守りが「常に接続中」の挙動になる）

## セットアップ

```bash
cp setting.json.example setting.json   # serial_port を実機のポートに（明示指定・§8）
```

- 常駐は yocron の `meds-daemon` ジョブ（`schedule = keepalive`・`alive_port = 8477`）
- リマインドは yocron の `meds-check` ジョブ（`every 5m`）

## データ（~/www-portal/data/meds/・git管理外）

| ファイル | 正 | 内容 |
|---|---|---|
| config.json | ポータル api.php | slots（朝/昼/夜 start-end+有効）・recommend（推奨時刻×3）・remind（追い通知間隔）・switches（6要素リスト） |
| status.json | daemon | daemon 状態・デバイス online/最終応答/最終服用 |
| history.jsonl | daemon | 服薬イベント追記ログ `{ts, iso, device, switch, switch_name, source}` |
| remind-state.json | meds_check | 当日の推奨時刻通知済みフラグ・追い通知の最終送信（二重通知防止） |

## テスト

```bash
# リマインドの「今」を差し替えて実行（タイトルに (テスト) が付く）
MEDS_FAKE_TIME=2026-10-08T08:35 ~/venv/bin/python3 meds_check.py
```
