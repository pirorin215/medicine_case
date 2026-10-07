# Medicine Case Pro Micro（スイッチ版）

Pro Micro (ATmega32U4) + マイクロスイッチによる **USB直結版** 服薬記録デバイス。
**ボタンを押したら「服薬した」**。押すとオンボードLEDが点滅して応答し、
USB シリアルで Mac daemon（`../meds-daemon/`）へ INTAKE を送る。

> 経緯: v2.0 は GY-BMI160 による傾き検知だったが、半年の運用で「反応するように
> 傾けている」状態になり、稀な不検知もあったため 2026-10-07 にスイッチ押下へ転換（v2.1）。
> 検知の確実さは押下が最強で、認知負荷も変わらないと判断。

## ハードウェア

| 部品 | 内容 |
|---|---|
| マイコン | Pro Micro互換 (ATmega32U4・5V/16MHz) |
| ボタン | 瞬間マイクロリミットスイッチ（SPDT・手持ち MKBKLLJY系）またはタクトスイッチ |
| 電源 | USB（Macポートから直接給電） |

### 配線（スイッチ1本につき2本・GNDは共有）

| スイッチ | Pro Micro | 備考 |
|---|---|---|
| COM（中央ピン） | GND | 全スイッチで共有可 |
| NO（a側ピン） | D4 / D5 / D6 / D7 / D8 / D9 | INPUT_PULLUP・押すと LOW。NC は未接続 |

- スイッチとピンの対応は固定: **D4=idx 0, D5=1, D6=2, D7=3, D8=4, D9=5**（`SWITCH_PINS`）
- 未配線のピンは放置でよい（内部プルアップで浮いたまま）
- スイッチの名前はファームでは持たず、Mac 側 config.json の `switches`（ポータルの⚙設定）で管理

タクトスイッチの場合も同様に片脚→GND・他方→対応GPIO。

## 検知仕様

- スイッチごとに独立: 30ms のチャタリング除去後の立ち下がり（押し込み確定）で1回だけ発火
- 同一スイッチの 2秒以内の連打は二重記録防止で無視（**別スイッチの同時押下は有効**・daemon 側 dedup もスイッチ単位）
- 押すとオンボードLED（RX/TX）が約1.5秒点滅（ノンブロッキング・LED_BLINK_* 定数・全スイッチ共通）。
  LED は active-low（RXLED0/TXLED0 が点灯）。実機で逆に見えたら
  `medicine_case_promicro_switch.ino` の LED_ON/LED_OFF を入れ替える
- daemon 未接続中の押下は1件だけ保持され、再接続時にスイッチidx＋経過ミリ秒付きで再送

## シリアルプロトコル（115200 baud・行ベーステキスト）

### MCU -> host

| 行 | 意味 |
|---|---|
| `HELLO medcase <name> v<x.y.z>` | 接続確立時（再接続時も再送） |
| `CONFIG name=<name> v=<x.y.z>` | HELLO 直後と GET:config への応答 |
| `HB <uptime_s>` | 60秒ごとの心拍 |
| `INTAKE <idx> <age_ms>` | 服薬押下（idx は D4=0〜D9=5）。age_ms>0 は daemon 停止中押下の再送（受信時刻から差し引いて復元） |

待機中は送信しない（v2.2）。コアの CDC が Serial.write 毎に TX LED をパルスするため、
待機中の点滅を減らすには送信を減らすしかない → 心拍は60秒に1回だけ。

### host -> MCU

| コマンド | 応答 |
|---|---|
| `PING` | `PONG` |
| `GET:config` | `CONFIG ...` |
| `SET:name:<name>` | `OK:name:<name>` / `ERR:name`（1-15文字） |

名前は EEPROM に保存される（複数台識別は Phase 2 で daemon・ポータルが扱う）。

## ビルド・書き込み

```bash
cp setting.sh.example setting.sh   # MEDICINE_CASE_PROMICRO_PORT を設定（開発中は212101）
bash compile.sh                    # ビルド
sh upload.sh                       # 書き込み（Caterina: リセット後8秒が書込窓）
./consolelog.sh                    # シリアルモニタ
```

- FQBN: `arduino:avr:leonardo`（Pro Micro 専用コア無しで Leonardo として扱う・promicro-presence と同じ）

## ライセンス

MIT License
