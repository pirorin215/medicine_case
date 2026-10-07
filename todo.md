# medicine_case todo

更新: 2026-10-07

## 残課題

### Phase 1: Pro Micro 単体版の構築（USB直結・ボタン押下で記録）

- [x] ファームウェア `medicine_case_promicro/`（**マイクロスイッチ押下版 v2.1**・シリアル契約・EEPROM設定・SET:name込み・ビルド合格 Flash 34%/RAM 14%）
- [x] Mac daemon `meds-daemon/`（シリアル監視・再接続・status/history書込・ntfy・alive_port・デバイス無音検知）— status.json 連携は api 実測済み
- [x] yocron ジョブ: `meds-daemon`（keepalive・alive_port 8477）＋ `meds-check`（every 5m 飲み忘れリマインド）— validate 合格・**ハード接続まで disabled マーカーで停止中**
- [x] ポータル `/meds/` 新設（デバイス状態・今日の3枠・履歴・設定）＋ `~/www-portal/index.html` にカード追加 — api.php 応答実測済み
- [ ] 実機配線（マイクロスイッチ: COM→GND / NO→D4 のみ・INPUT_PULLUP）と書き込み検証（開発ポート212101）。**接続時に `rm ~/.cache/yocron/disabled/meds-daemon ~/.cache/yocron/disabled/meds-check` でジョブ有効化**
- [ ] E2E実証: 薬ケース実機で旧BLE版と並行運用→安定後、XIAO BLE Sense を取り外して fastrec 予備へ
- [ ] `/parts/` で Pro Micro −1 の消費記録（スイッチはタクト/リミットスイッチの手持ちから・消したら記録）
- [ ] 運用固定ポートが決まったら `~/dev/Arduino/AGENTS.md` §8 シリアルポート対応表へ登録

### Phase 2: 複数台対応（Phase 1 が安定稼働してから着手）

- [ ] daemon: ポート→デバイス名のレジストリ（daemon 設定に複数 Pro Micro の固定ポートを列挙・HELLO の名前と突合）
- [ ] history/status をデバイス名単位に分離（history.jsonl には Phase 1 から device フィールド入りなので拡張は読み側）
- [ ] ポータル: デバイス切替UI・台数分の状態表示・台帳的な名前管理
- [ ] 台数分の §8 シリアルポート対応表登録

## 概要

- 旧BLE版（XIAO BLE Sense＋MedicineCaseMobアプリ）を実機運用で使えているため残置。
- Phase 1 完成後も旧版は予備として残し、並行運用で精度を確認してから Sense を解放する。
- リマインド時刻の既定値は 朝8:00／昼12:30／夜22:00（ポータルで変更可）。

## 仕様変更の経緯

- **2026-10-07: 傾き検知 → マイクロスイッチ押下へ転換（v2.0 → v2.1）。**
  BLE版を半年使った結果、「服用動作に合わせて自然に傾く」のではなく
  「反応するように傾けている」ことに気づいた。極稀に反応しないこともあり、
  だったらボタンを押すのと認知負荷は同じ。スマホ直接操作は動線が違うので却下。
  物理クリックボタン好みの既知嗜好とも一致。**検知の確実さは押下が最強。**
- GY-BMI160 は本件で不要に（未消費・傾き検知のBMI160なし）。
  daemon・ポータルからしきい値（角度/クールダウン）設定と pitch/roll 表示を除去済み。
