# medicine_case todo

更新: 2026-10-08

## 残課題

- [ ] ファーム v2.3.1 を実機へ書き込み（2026-10-08 リスクスキャン対処・ビルド合格済み。書込は `sh upload.sh` — daemon がポート占有中のため、書込前に `~/.cache/yocron/disabled/meds-daemon` を作って daemon を停止し、書込後にマーカー削除して keepalive 再起動が確実）
- [ ] daemon: ポート→デバイス名のレジストリ（daemon 設定に複数 Pro Micro の固定ポートを列挙・HELLO の名前と突合）
- [ ] history/status をデバイス名単位に分離（history.jsonl には Phase 1 から device フィールド入りなので拡張は読み側）
- [ ] ポータル: デバイス切替UI・台数分の状態表示・台帳的な名前管理

## 保留

## 完了済み

### リスクスキャン対処（2026-10-08 朝スキャンの指摘）

- [x] ファーム v2.3.1: 未送信押下をスイッチ単位保持に — daemon 未接続中の複数押下で pending 単数スロットが上書きされ古い分が消失する問題（スキャン #1・中）。別スイッチは全て残る・同一スイッチ複数回は初回時刻で束ねる。ビルド合格 Flash 36%/RAM 16%
- [x] daemon: SIGTERM ハンドラ追加 — yocron timeout(24h)・手動 stop で finally がスキップされ status.json が connected=true のまま残留する問題（スキャン #2・低）。SIGTERM→sys.exit(0) で run_serial の finally を通る
- [x] meds_check: config 形状ガイドを全枠 start/end 必須に — 先頭だけ新形式だと 5 分毎に KeyError でリマインド job が失敗し続ける問題（スキャン #3・低）。単体テスト 4 ケース PASS
- [x] ポータル api.php（~/www/meds）: save_json の tmp をプロセス単位ユニークに・書込失敗検知（スキャン #4・低）

### Phase 1: Pro Micro 単体版の構築（USB直結・ボタン押下で記録）

- [x] ファームウェア `medicine_case_promicro/`（**マイクロスイッチ押下版 v2.1**・シリアル契約・EEPROM設定・SET:name込み・ビルド合格 Flash 34%/RAM 14%）
- [x] Mac daemon `meds-daemon/`（シリアル監視・再接続・status/history書込・ntfy・alive_port・デバイス無音検知）— status.json 連携は api 実測済み
- [x] yocron ジョブ: `meds-daemon`（keepalive・alive_port 8477）＋ `meds-check`（every 5m 飲み忘れリマインド）— validate 合格・**ハード接続まで disabled マーカーで停止中**
- [x] ポータル `/meds/` 新設（デバイス状態・今日の3枠・履歴・設定）＋ `~/www-portal/index.html` にカード追加 — api.php 応答実測済み
- [x] **移設完了: `/dev/cu.usbmodem212401`（2026-10-07・ユーザーが移設・setting.sh更新済み）** — daemon setting.json 更新＋再起動でオンライン確認済み・§8 表へ「常用固定・実験流用書込禁止」で登録済み。自プロジェクトの upload.sh は自ポートへの書込が正当なので common.sh blocklist には入れない（他プロジェクトは §8 宣言で守る）

### Phase 2: 同一デバイス複数スイッチ対応（2026-10-07 ユーザー提案・同日実装）

- [x] ファーム v2.3: SWITCH_PINS = {D4, D5, D6, D7, D8, D9}（最大6本・INPUT_PULLUP・GND共有）・`INTAKE <idx> <age_ms>`・押下LED点滅は全スイッチ共通（ビルド合格）
- [x] daemon: dedup をスイッチ単位に変更・スイッチ名は config.json の `switches: {idx: 名前}` で Mac 側管理（EEPROM に焼かない＝名前変更にファーム不要）・v2.2 互換（`INTAKE <age>` は idx=0 扱い）
- [x] ポータル: 設定にスイッチ名一覧（D4-D9→名前）を追加・history/ntfy に switch_name を反映
- [x] スケジュールをスマホアプリ準拠に（2026-10-07）: 枠=開始/終了(連動)＋推奨時刻3つ(有効枠内のみ・1日1回)＋守りは直近枠ルール＋追い通知60分間隔。帰属は時刻ベース（旧GRACE吸収は廃止）。MEDS_FAKE_TIME で攻め/守り/dedup/日替わりをテスト済み。BLE接続時のチャンス通知のみ非移植

- メリット: 用途が増えても USB 1ポートで済む → ポート追加も複数台化（Phase 3）も当面不要になる

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
