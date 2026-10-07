/**
 * BMI160 ドライバ + 角度計算 + 服薬検知状態機械
 *
 * BMI160 部は bikeclock_esp32/bikeclock_esp32_imu.ino の Wire.h レジスタ直叩きを移植
 * （CMD 受理待ち・PMU 完了待ちの罠と対策も同様。加速度のみ使用しジャイロは起動しない）。
 * 角度計算・平滑化・状態機械は BLE版 medicine_case_sensor.ino から無変更移植
 * （閾値・クールダウンは EEPROM 設定 g_cfg を参照する点のみ差し替え）。
 */

#include "medicine_case_promicro.h"

// --- BMI160 レジスタ（datasheet reference・bikeclock_esp32 と同一）---
#define BMI160_CHIPID      0x00   // Chip ID（期待値 0xD1）
#define BMI160_ERR_REG     0x02
#define BMI160_PMU_STATUS  0x03
#define BMI160_ACC_X_L     0x12   // acc x,y,z の 6B 連続読出し
#define BMI160_ACC_CONF    0x40
#define BMI160_ACC_RANGE   0x41
#define BMI160_CMD         0x7E
#define BMI160_CHIPID_VAL  0xD1

// PMU_STATUS の bits[5:4] = 加速度 (00=suspend, 01=normal, 10=low_power)
#define BMI160_PMU_ACC_MASK   0x30
#define BMI160_PMU_ACC_NORMAL 0x10

#define BMI160_ACC_LSB_PER_G  16384.0f  // ±2g (ACC_RANGE=0x03)

// ACC_CONF = (bwp=2 normal << 4) | odr=8(100Hz) = 0x28
// （100ms 周期の読出しに合わせ 100Hz。bikeclock_esp32 は 50ms 周期で 0x27=50Hz）
#define BMI160_ACC_CONF_VAL   0x28

bool g_imuEnabled = false;
static float g_imuAx = 0.0f, g_imuAy = 0.0f, g_imuAz = 0.0f;  // [g]
static unsigned long g_imuLastSampleMillis = 0;

//=============================================================================
// I2C ヘルパ（レジスタ読み書き・bikeclock_esp32 と同一）
//=============================================================================
static bool imuWriteReg(uint8_t reg, uint8_t val) {
    Wire.beginTransmission(IMU_I2C_ADDR);
    Wire.write(reg);
    Wire.write(val);
    return (Wire.endTransmission() == 0);
}

static uint8_t imuReadReg(uint8_t reg) {
    Wire.beginTransmission(IMU_I2C_ADDR);
    Wire.write(reg);
    Wire.endTransmission(false);   // repeated start
    Wire.requestFrom((int)IMU_I2C_ADDR, 1);
    if (Wire.available()) return Wire.read();
    return 0;
}

static bool imuReadRegs(uint8_t reg, uint8_t* buf, uint8_t len) {
    Wire.beginTransmission(IMU_I2C_ADDR);
    Wire.write(reg);
    if (Wire.endTransmission(false) != 0) return false;
    Wire.requestFrom((int)IMU_I2C_ADDR, (int)len);
    for (uint8_t i = 0; i < len; i++) {
        if (!Wire.available()) return false;
        buf[i] = Wire.read();
    }
    return true;
}

// PMU_STATUS が指定値になるまで待つ（BMI160 は前 CMD 処理中の次 CMD を無視する）
static bool imuWaitPmu(uint8_t mask, uint8_t val, uint32_t timeoutMs) {
    const uint32_t start = millis();
    while ((millis() - start) < timeoutMs) {
        if ((imuReadReg(BMI160_PMU_STATUS) & mask) == val) return true;
        delay(2);
    }
    return false;
}

static bool imuWaitCmdDone(uint32_t timeoutMs) {
    const uint32_t start = millis();
    while ((millis() - start) < timeoutMs) {
        if (imuReadReg(BMI160_CMD) == 0x00) return true;
        delay(1);
    }
    return false;
}

// NORMAL モード移行（CMD 受理待ち + PMU 確認をリトライ付きで）
static bool imuEnableAccel() {
    for (int retry = 0; retry < 5; retry++) {
        imuWriteReg(BMI160_CMD, 0x11);  // ACC_NORMAL
        delay(2);
        imuWaitCmdDone(10);
        if (imuWaitPmu(BMI160_PMU_ACC_MASK, BMI160_PMU_ACC_NORMAL, 120)) return true;
    }
    return false;
}

//=============================================================================
// setupIMU — BMI160 初期化
//=============================================================================
void setupIMU() {
    Wire.begin();            // Pro Micro: SDA=D2 / SCL=D3（ハード固定）
    Wire.setClock(400000);
    delay(50);

    uint8_t chipId = imuReadReg(BMI160_CHIPID);
    if (chipId != BMI160_CHIPID_VAL) {
        logInfo("IMU", String(F("BMI160 NOT detected (chipId=0x")) + String(chipId, HEX) +
                F(" expected 0xD1) — 配線を確認（検知不能のまま起動）"));
        g_imuEnabled = false;
        return;
    }

    // ソフトリセット（リセット後は全センサ SUSPEND）
    imuWriteReg(BMI160_CMD, 0xB6);
    delay(50);

    if (!imuEnableAccel()) {
        logInfo("IMU", F("WARN: ACC NORMAL failed"));
    }

    imuWriteReg(BMI160_ACC_RANGE, 0x03);               // ±2g
    imuWriteReg(BMI160_ACC_CONF, BMI160_ACC_CONF_VAL); // 100Hz, bwp=normal
    delay(2);

    g_imuEnabled = true;
    uint8_t pmu = imuReadReg(BMI160_PMU_STATUS);
    uint8_t err = imuReadReg(BMI160_ERR_REG);
    logInfo("IMU", String(F("BMI160 ready PMU=0x")) + String(pmu, HEX) +
            F(" ERR=0x") + String(err, HEX));
}

//=============================================================================
// 角度計算（BLE版 calculateAngles と同一・ソースが BMI160 になっただけ）
//=============================================================================
static void calculateAngles(float* pitch, float* roll) {
    *pitch = atan2(g_imuAy, sqrt(g_imuAx * g_imuAx + g_imuAz * g_imuAz)) * 180.0 / PI;
    *roll  = atan2(-g_imuAx, sqrt(g_imuAy * g_imuAy + g_imuAz * g_imuAz)) * 180.0 / PI;

    if (*pitch > 180.0) *pitch -= 360.0;
    if (*roll  > 180.0) *roll  -= 360.0;
}

//=============================================================================
// updateSensor — 100ms 周期でサンプリングし EMA 平滑
//=============================================================================
void updateSensor() {
    static unsigned long lastSensorUpdate = 0;

    if (g_currentMillis - lastSensorUpdate < SENSOR_UPDATE_INTERVAL_MS) {
        return;
    }
    lastSensorUpdate = g_currentMillis;

    if (!g_imuEnabled) return;

    uint8_t buf[6];
    if (!imuReadRegs(BMI160_ACC_X_L, buf, 6)) {
        return;   // I2C 読み失敗は次回リトライ
    }
    int16_t ax = (int16_t)((buf[1] << 8) | buf[0]);
    int16_t ay = (int16_t)((buf[3] << 8) | buf[2]);
    int16_t az = (int16_t)((buf[5] << 8) | buf[4]);
    g_imuAx = ax / BMI160_ACC_LSB_PER_G;
    g_imuAy = ay / BMI160_ACC_LSB_PER_G;
    g_imuAz = az / BMI160_ACC_LSB_PER_G;

    float newPitch, newRoll;
    calculateAngles(&newPitch, &newRoll);

    // 平滑化（単純移動平均・BLE版と同じ alpha=0.8）
    const float alpha = 0.8f;
    g_currentPitch = alpha * g_currentPitch + (1.0f - alpha) * newPitch;
    g_currentRoll  = alpha * g_currentRoll  + (1.0f - alpha) * newRoll;
}

//=============================================================================
// detectMedicineIntake — 服薬検知状態機械（BLE版 detectMedicineIntake の移植）
//
// IDLE:       クールダウン確認 → ベースライン捕捉/更新（1秒安定で） → 大きな動きで MOVING へ
// MOVING:     MOVEMENT_STABILITY_MS 後に最大変化量を判定し閾値以上なら検出
// CONFIRMED:  即座に IDLE へ戻す（BLE版と同一）
// 検出時: serialSendIntake(maxChange) でシリアル送出（BLE版はここでINTAKE通知+LED点灯。
//         Pro Micro に RGB LED はないためLEDフィードバックは省略・通知は daemon の ntfy が担う）
//=============================================================================
bool detectMedicineIntake() {
    static float initialPitch = 0.0f;
    static float initialRoll = 0.0f;
    static unsigned long movementStartTime = 0;
    static bool initialPositionSet = false;
    static float maxChange = 0.0f;
    static unsigned long lastDetectionTime = 0;

    // ベースライン捕捉/更新用の安定性トラッキング
    static unsigned long stableDurationStart = 0;
    static float lastStablePitch = 0.0f;
    static float lastStableRoll = 0.0f;

    float pitchChange, rollChange, totalChange;

    switch (g_detectionState) {
        case DETECTION_STATE_IDLE:
            // クールダウン中はスキップ
            if (lastDetectionTime > 0) {
                if (g_currentMillis - lastDetectionTime < g_cfg.cooldownMs) {
                    break;
                }
                initialPositionSet = false;
                maxChange = 0.0f;
                logInfo("SENSOR", F("Cooldown expired. Ready for next detection."));
                lastDetectionTime = 0;
            }

            // ベースラインの捕捉/更新（2度未満の動きが1秒続いたら安定とみなす）
            {
                float diff = sqrt(pow(g_currentPitch - lastStablePitch, 2) +
                                  pow(g_currentRoll - lastStableRoll, 2));
                if (diff < 2.0f) {
                    if (stableDurationStart == 0) {
                        stableDurationStart = g_currentMillis;
                    } else if (g_currentMillis - stableDurationStart > 1000) {
                        bool shouldSet = false;

                        if (!initialPositionSet) {
                            shouldSet = true;
                            logInfo("SENSOR", String(F("Initial position set: Pitch=")) +
                                     String(g_currentPitch, 1) + F(" Roll=") + String(g_currentRoll, 1));
                        } else {
                            // 別位置で安定し続けた場合はベースラインを更新
                            float distFromInitial = sqrt(pow(g_currentPitch - initialPitch, 2) +
                                                         pow(g_currentRoll - initialRoll, 2));
                            if (distFromInitial > 5.0f) {
                                shouldSet = true;
                                logInfo("SENSOR", F("Baseline updated (drifted)"));
                            }
                        }

                        if (shouldSet) {
                            initialPitch = g_currentPitch;
                            initialRoll = g_currentRoll;
                            maxChange = 0.0f;
                            initialPositionSet = true;
                        }
                    }
                } else {
                    lastStablePitch = g_currentPitch;
                    lastStableRoll = g_currentRoll;
                    stableDurationStart = g_currentMillis;
                }
            }

            if (!initialPositionSet) {
                break;
            }

            pitchChange = fabs(g_currentPitch - initialPitch);
            rollChange = fabs(g_currentRoll - initialRoll);
            totalChange = sqrt(pitchChange * pitchChange + rollChange * rollChange);

            if (totalChange > maxChange) {
                maxChange = totalChange;
            }

            if (totalChange > g_cfg.angle) {
                g_detectionState = DETECTION_STATE_MOVING;
                movementStartTime = g_currentMillis;
            }
            break;

        case DETECTION_STATE_MOVING:
            pitchChange = fabs(g_currentPitch - initialPitch);
            rollChange = fabs(g_currentRoll - initialRoll);
            totalChange = sqrt(pitchChange * pitchChange + rollChange * rollChange);

            if (totalChange > maxChange) {
                maxChange = totalChange;
            }

            if (g_currentMillis - movementStartTime > MOVEMENT_STABILITY_MS) {
                if (maxChange >= g_cfg.angle) {
                    logInfo("SENSOR", String(F("Medicine intake detected! ")) + String(maxChange, 1) + F(" deg"));
                    serialSendIntake(maxChange);

                    g_detectionState = DETECTION_STATE_CONFIRMED;

                    // クールダウン開始・次回検出用にリセット
                    lastDetectionTime = g_currentMillis;
                    initialPositionSet = false;
                    maxChange = 0.0f;
                } else {
                    g_detectionState = DETECTION_STATE_IDLE;
                    initialPositionSet = false;
                    maxChange = 0.0f;
                }
            }
            break;

        case DETECTION_STATE_CONFIRMED:
            g_detectionState = DETECTION_STATE_IDLE;
            initialPositionSet = false;
            maxChange = 0.0f;
            break;
    }

    return false;
}
