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

### 配線（2本だけ）

| スイッチ | Pro Micro | 備考 |
|---|---|---|
| COM（中央ピン） | GND | |
| NO（a側ピン） | D4 | INPUT_PULLUP・押すと LOW。NC は未接続 |

タクトスイッチの場合も同様に片脚→GND・他方→D4。

## 検知仕様

- 30ms のチャタリング除去後の立ち下がり（押し込み確定）で1回だけ発火
- 2秒以内の連打は二重記録防止で無視（daemon 側にも5秒 dedup あり）
- 押すとオンボードLED（RX/TX）が約1.5秒点滅（ノンブロッキング・LED_BLINK_* 定数）
- daemon 未接続中の押下は保持され、再接続時に経過ミリ秒付きで1回だけ再送

## シリアルプロトコル（115200 baud・行ベーステキスト）

### MCU -> host

| 行 | 意味 |
|---|---|
| `HELLO medcase <name> v<x.y.z>` | 接続確立時（再接続時も再送） |
| `CONFIG name=<name> v=<x.y.z>` | HELLO 直後と GET:config への応答 |
| `HB <uptime_s>` | 5秒ごとの心拍 |
| `T <state>` | 1秒ごとのテレメトリ（state: IDLE/BLINK） |
| `INTAKE <age_ms>` | 服薬押下。age_ms>0 は daemon 停止中押下の再送（受信時刻から差し引いて復元） |

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
