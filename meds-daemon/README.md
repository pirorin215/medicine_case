# meds-daemon

Medicine Case Pro Micro（`../medicine_case_promicro/`）の USB シリアルを受ける
Mac mini 常駐 daemon。旧BLE版で Android アプリ（MedicineCaseMob）が担っていた
記録・通知・スケジュール管理を Mac 側に引き継ぐ。

## 構成

```
Pro Micro → USB シリアル → meds_daemon.py（常駐・yocron keepalive 配下）
                              ├─ history.jsonl 追記 + ntfy 即時通知（服用記録）
                              ├─ status.json 更新（デバイス状態・ポータルが読む）
                              ├─ config.json の差分を SET:angle/cooldown でMCUへ反映
                              └─ 設定: setting.json（git管理外・exampleからコピー）
meds_check.py（yocron every 5m）→ 飲み忘れを ntfy リマインド
```

## セットアップ

```bash
cp setting.json.example setting.json   # serial_port を実機のポートに（明示指定・§8）
```

- 常駐は yocron の `meds-daemon` ジョブ（`schedule = keepalive`・`alive_port = 8477`）
- リマインドは yocron の `meds-check` ジョブ（`every 5m`）

## データ（~/www-portal/data/meds/・git管理外）

| ファイル | 正 | 内容 |
|---|---|---|
| config.json | ポータル api.php | slots（朝/昼/夜の時刻+有効）・remind・device（角度/クールダウン） |
| status.json | daemon | daemon 状態・デバイス online/角度/状態/最終心拍 |
| history.jsonl | daemon | 服薬イベント追記ログ `{ts, iso, device, max_change, source}` |
| remind-state.json | meds_check | 当日のリマインド送信履歴（二重通知防止） |

## テスト

```bash
# リマインドの「今」を差し替えて実行（タイトルに (テスト) が付く）
MEDS_FAKE_TIME=2026-10-08T08:35 ~/venv/bin/python3 meds_check.py
```
