# Medicine Case Pro Micro

Pro Micro (ATmega32U4) + GY-BMI160 による **USB直結版** スマート薬ケースファームウェア。
旧BLE版（`../medicine_case/`・XIAO BLE Sense）の検知ロジックをそのまま移植し、
BLE を USB シリアルに置き換えたもの。検知はすべて MCU 側で完結し、Mac の
daemon（`../meds-daemon/`）がイベントを受けて記録・通知する。

## ハードウェア

| 部品 | 内容 |
|---|---|
| マイコン | Pro Micro互換 (ATmega32U4・5V/16MHz) |
| センサ | GY-BMI160 (6軸IMU・加速度のみ使用・ジャイロは未起動) |
| 電源 | USB（Macポートから直接給電） |

### 配線

| GY-BMI160 | Pro Micro | 備考 |
|---|---|---|
| VCC | VCC (5V) | モジュール内蔵LDOで3.3Vを生成 |
| GND | GND | |
| SDA | D2 (SDA) | モジュール側プルアップ3.3V。AVRのVIH(0.6VCC=3.0V)に規格内で収まる |
| SCL | D3 (SCL) | 同上 |
| SDO | GND | I2Cアドレス 0x68（VCC接続なら0x69・`IMU_I2C_ADDR`を変更） |

## シリアルプロトコル（115200 baud・行ベーステキスト）

### MCU -> host

| 行 | 意味 |
|---|---|
| `HELLO medcase <name> v<x.y.z>` | 接続確立時（再接続時も再送） |
| `CONFIG angle=<deg> cooldown=<ms> name=<name> v=<x.y.z>` | HELLO 直後と GET:config への応答 |
| `HB <uptime_s>` | 5秒ごとの心拍 |
| `T <pitch> <roll> <state>` | 1秒ごとのテレメトリ（state: IDLE/MOVING/CONFIRMED） |
| `INTAKE <maxChange> <age_ms>` | 服薬検知。age_ms>0 は daemon 停止中検知の再送（受信時刻から差し引いて復元） |

### host -> MCU

| コマンド | 応答 | 範囲 |
|---|---|---|
| `PING` | `PONG` | |
| `GET:config` | `CONFIG ...` | |
| `SET:angle:<deg>` | `OK:angle:<deg>` / `ERR:angle` | 10-180 |
| `SET:cooldown:<ms>` | `OK:cooldown:<ms>` / `ERR:cooldown` | 1000-300000 |
| `SET:name:<name>` | `OK:name:<name>` / `ERR:name` | 1-15文字 |

設定変更は即EEPROMに保存され、電源断後も保持される。
threshold/cooldown は daemon が `~/www-portal/data/meds/config.json` との差分を見て自動送信するため、通常は手動で打つ必要はない。

## 検知仕様（BLE版から無変更移植）

- pitch/roll を重力ベクトルから計算（100ms周期）し EMA 平滑（alpha=0.8）
- 安定（2度未満の動きが1秒）したらベースラインを捕捉。別位置で安定し続けたら更新（ドリフト追従）
- ベースラインからの変化量が角度しきい値（既定70°）を超えたら MOVING、500ms後に最大変化量で確定判定
- 検出後はクールダウン（既定30秒）で連続検出を防止

## ビルド・書き込み

```bash
cp setting.sh.example setting.sh   # MEDICINE_CASE_PROMICRO_PORT を設定（開発中は212101）
bash compile.sh                    # ビルド
sh upload.sh                       # 書き込み（Caterina: リセット後8秒が書込窓）
./consolelog.sh                    # シリアルモニタ
```

- FQBN: `arduino:avr:leonardo`（Pro Micro 専用コア無しで Leonardo として扱う・promicro-presence と同じ）
- 複数台の識別は `SET:name:<name>` で EEPROM に名前を焼いて行う（Phase 2 で daemon・ポータルが扱う）

## ライセンス

MIT License
