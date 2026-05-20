/**
 * LEDベースのデバッグシステム
 * Serial通信が使えない場合の代替デバッグ方法
 */

#ifndef LED_DEBUG_H
#define LED_DEBUG_H

// LEDデバッグパターン
enum LedDebugPattern {
    LED_DEBUG_BOOT,          // 起動完了: 紫色3回点滅
    LED_DEBUG_SENSOR_OK,     // センサーOK: 緑色2回点滅
    LED_DEBUG_SENSOR_ERROR,  // センサーNG: 赤色5回点滅
    LED_DEBUG_BLE_OK,        // BLE OK: 青色2回点滅
    LED_DEBUG_INTAKE_DETECTED, // 服薬検知: 青色5秒点灯
    LED_DEBUG_ERROR          // エラー: 赤色高速点滅
};

// LEDデバッグ関数
void ledDebugShowPattern(LedDebugPattern pattern) {
    switch (pattern) {
        case LED_DEBUG_BOOT:
            // 紫色3回点滅（起動完了）
            for (int i = 0; i < 3; i++) {
                setLedColor(true, false, true);  // Purple
                delay(200);
                setLedColor(false, false, false);  // Off
                delay(200);
            }
            break;

        case LED_DEBUG_SENSOR_OK:
            // 緑色2回点滅（センサーOK）
            for (int i = 0; i < 2; i++) {
                setLedColor(false, true, false);  // Green
                delay(200);
                setLedColor(false, false, false);  // Off
                delay(200);
            }
            break;

        case LED_DEBUG_SENSOR_ERROR:
            // 赤色5回点滅（センサーNG）
            for (int i = 0; i < 5; i++) {
                setLedColor(true, false, false);  // Red
                delay(100);
                setLedColor(false, false, false);  // Off
                delay(100);
            }
            break;

        case LED_DEBUG_BLE_OK:
            // 青色2回点滅（BLE OK）
            for (int i = 0; i < 2; i++) {
                setLedColor(false, false, true);  // Blue
                delay(200);
                setLedColor(false, false, false);  // Off
                delay(200);
            }
            break;

        case LED_DEBUG_INTAKE_DETECTED:
            // 青色5秒点灯（服薬検知）
            setLedColor(false, false, true);  // Blue
            delay(5000);
            setLedColor(false, false, false);  // Off
            break;

        case LED_DEBUG_ERROR:
            // 赤色高速点滅（エラー）
            while (true) {
                setLedColor(true, false, false);  // Red
                delay(50);
                setLedColor(false, false, false);  // Off
                delay(50);
            }
            break;
    }
}

// 角度情報をLEDで表現（簡易版）
void ledDebugShowAngle(float pitch, float roll) {
    // ピッチ角度で色を決定
    if (fabs(pitch) > 90.0) {
        setLedColor(true, false, false);  // Red: Large pitch change
    } else if (fabs(roll) > 90.0) {
        setLedColor(false, false, true);  // Blue: Large roll change
    } else {
        setLedColor(false, true, false);  // Green: Stable
    }
}

#endif // LED_DEBUG_H
