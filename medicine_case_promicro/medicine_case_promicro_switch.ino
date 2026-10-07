/**
 * マイクロスイッチ入力（複数対応）+ オンボードLED点滅
 *
 * スイッチ: COM -> GND / NO -> GPIO（D4-D9・GND は共有）。INPUT_PULLUP で押すと LOW。
 *   各スイッチ独立に 30ms のチャタリング除去 → 立ち下がり（押し込み確定）で1回だけ発火。
 *   同一スイッチの 2秒以内の連打は二重記録防止で無視（別スイッチの同時押下は有効）。
 *
 * LED: 押されたら RX/TX オンボードLEDを約1.5秒間点滅（ノンブロッキング・全スイッチ共通）。
 *   ポリティは active-low（RXLED0/TXLED0 が点灯）。実機で逆に見えたら
 *   LED_ON/LED_OFF のマクロ内を入れ替えるだけでよい。
 */

#include "medicine_case_promicro.h"

// active-low なので 0 マクロが点灯
#define LED_ON()  do { RXLED0; TXLED0; } while (0)
#define LED_OFF() do { RXLED1; TXLED1; } while (0)

//=============================================================================
// スイッチ入力（スイッチごとに独立したデバウンス状態）
//=============================================================================
static const uint8_t SWITCH_PIN_LIST[SWITCH_COUNT] = SWITCH_PINS;

static uint8_t s_stable[SWITCH_COUNT];            // 直近の安定値
static uint8_t s_lastRaw[SWITCH_COUNT];
static unsigned long s_lastRawChange[SWITCH_COUNT];
static unsigned long s_lastEvent[SWITCH_COUNT];

void setupSwitch() {
    for (uint8_t i = 0; i < SWITCH_COUNT; i++) {
        pinMode(SWITCH_PIN_LIST[i], INPUT_PULLUP);
        s_stable[i] = HIGH;
        s_lastRaw[i] = HIGH;
        s_lastRawChange[i] = 0;
        s_lastEvent[i] = 0;
    }
    LED_OFF();
}

static void onPress(uint8_t idx) {
    if (s_lastEvent[idx] != 0 && g_currentMillis - s_lastEvent[idx] < SWITCH_MIN_INTERVAL_MS) {
        logInfo("SW", String(F("press ignored (idx=")) + idx + F(" too soon)"));
        return;
    }
    s_lastEvent[idx] = g_currentMillis;
    logInfo("SW", String(F("Medicine intake (switch idx=")) + idx + F(" pressed)"));
    serialSendIntake(idx);
    blinkStart();
}

void pollSwitch() {
    for (uint8_t i = 0; i < SWITCH_COUNT; i++) {
        uint8_t raw = digitalRead(SWITCH_PIN_LIST[i]);
        if (raw != s_lastRaw[i]) {
            s_lastRaw[i] = raw;
            s_lastRawChange[i] = g_currentMillis;
        } else if (raw != s_stable[i] && (g_currentMillis - s_lastRawChange[i]) >= SWITCH_DEBOUNCE_MS) {
            s_stable[i] = raw;
            if (s_stable[i] == LOW) {
                onPress(i);
            }
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
        }
    } else {
        LED_ON();
        s_ledOn = true;
    }
}
