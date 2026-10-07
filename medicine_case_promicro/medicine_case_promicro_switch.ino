/**
 * マイクロスイッチ入力 + オンボードLED点滅
 *
 * スイッチ: COM -> GND / NO -> D4。INPUT_PULLUP で押すと LOW。
 *   30ms のチャタリング除去後の立ち下がり（押し込み確定）で1回だけ発火。
 *   2秒以内の連打は二重記録防止のため無視（daemon 側にも5秒dedupあり）。
 *
 * LED: 押されたら RX/TX オンボードLEDを約1.5秒間点滅（ノンブロッキング）。
 *   ポリティは active-low（RXLED0/TXLED0 が点灯）。実機で逆に見えたら
 *   LED_ON/LED_OFF のマクロ内を入れ替えるだけでよい。
 */

#include "medicine_case_promicro.h"

// active-low なので 0 マクロが点灯
#define LED_ON()  do { RXLED0; TXLED0; } while (0)
#define LED_OFF() do { RXLED1; TXLED1; } while (0)

//=============================================================================
// スイッチ入力
//=============================================================================
static uint8_t s_stable = HIGH;            // 直近の安定値
static uint8_t s_lastRaw = HIGH;
static unsigned long s_lastRawChange = 0;
static unsigned long s_lastEvent = 0;

void setupSwitch() {
    pinMode(SWITCH_PIN, INPUT_PULLUP);
    LED_OFF();
}

static void onPress() {
    if (s_lastEvent != 0 && g_currentMillis - s_lastEvent < SWITCH_MIN_INTERVAL_MS) {
        logInfo("SW", F("press ignored (too soon)"));
        return;
    }
    s_lastEvent = g_currentMillis;
    logInfo("SW", F("Medicine intake (switch pressed)"));
    serialSendIntake();
    blinkStart();
}

void pollSwitch() {
    uint8_t raw = digitalRead(SWITCH_PIN);
    if (raw != s_lastRaw) {
        s_lastRaw = raw;
        s_lastRawChange = g_currentMillis;
    } else if (raw != s_stable && (g_currentMillis - s_lastRawChange) >= SWITCH_DEBOUNCE_MS) {
        s_stable = raw;
        if (s_stable == LOW) {
            onPress();
        }
    }
}

//=============================================================================
// LED 点滅（ノンブロッキング・押下フィードバック）
//=============================================================================
static bool s_blinkActive = false;
static uint8_t s_blinkCount = 0;
static bool s_ledOn = false;
static unsigned long s_blinkLast = 0;

void blinkStart() {
    s_blinkActive = true;
    s_blinkCount = 0;
    s_ledOn = false;
    s_blinkLast = g_currentMillis;
    g_mcuState = MCU_STATE_BLINK;
}

void updateBlink() {
    if (!s_blinkActive) return;

    const unsigned long interval = s_ledOn ? LED_BLINK_ON_MS : LED_BLINK_OFF_MS;
    if (g_currentMillis - s_blinkLast < interval) return;
    s_blinkLast = g_currentMillis;

    if (s_ledOn) {
        LED_OFF();
        s_ledOn = false;
        s_blinkCount++;
        if (s_blinkCount >= LED_BLINK_COUNT) {
            s_blinkActive = false;
            g_mcuState = MCU_STATE_IDLE;
        }
    } else {
        LED_ON();
        s_ledOn = true;
    }
}
