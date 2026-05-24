/**
 * 6-Axis Sensor Implementation for Medicine Case
 *
 * Uses LSM6DS3 accelerometer + gyroscope to detect medicine intake
 * by detecting large angle changes.
 *
 * On detection: stores timestamp in g_lastIntakeTimestamp and
 * sends INTAKE:<timestamp> via BLE sensor notification.
 */

#include "medicine_case.h"

// --- Sensor Setup ---
void setupSensor() {
    Serial.println("SENSOR: Initializing LSM6DS3...");
    Serial.flush();

    g_lsm6ds3 = new LSM6DS3(I2C_MODE, 0x6A);

    if (g_lsm6ds3->begin() != 0) {
        Serial.println("SENSOR: Failed to find LSM6DS3 chip!");
        Serial.flush();
        setLedError();
        while (1) {
            delay(10);
        }
    }

    Serial.println("SENSOR: LSM6DS3 found!");
    Serial.flush();
}

// --- Calculate Pitch and Roll Angles ---
void calculateAngles(float* pitch, float* roll) {
    float ax = g_lsm6ds3->readFloatAccelX();
    float ay = g_lsm6ds3->readFloatAccelY();
    float az = g_lsm6ds3->readFloatAccelZ();

    *pitch = atan2(ay, sqrt(ax * ax + az * az)) * 180.0 / PI;
    *roll = atan2(-ax, sqrt(ay * ay + az * az)) * 180.0 / PI;

    if (*pitch > 180.0) *pitch -= 360.0;
    if (*roll > 180.0) *roll -= 360.0;
}

// --- Update Sensor Data ---
void updateSensor() {
    static unsigned long lastSensorUpdate = 0;

    if (g_currentMillis - lastSensorUpdate < SENSOR_UPDATE_INTERVAL_MS) {
        return;
    }

    lastSensorUpdate = g_currentMillis;

    float newPitch, newRoll;
    calculateAngles(&newPitch, &newRoll);

    // Smooth the angles (simple moving average)
    const float alpha = 0.8f;
    g_currentPitch = alpha * g_currentPitch + (1.0f - alpha) * newPitch;
    g_currentRoll = alpha * g_currentRoll + (1.0f - alpha) * newRoll;

    // Log sensor data (every 1 second)
    static unsigned long lastLogTime = 0;
    if (g_currentMillis - lastLogTime >= 1000) {
        const char* stateName = "";
        switch (g_detectionState) {
            case DETECTION_STATE_IDLE: stateName = "IDLE"; break;
            case DETECTION_STATE_MOVING: stateName = "MOVING"; break;
            case DETECTION_STATE_STABILIZING: stateName = "STABILIZING"; break;
            case DETECTION_STATE_CONFIRMED: stateName = "CONFIRMED"; break;
        }

        logPrint("SENSOR", "Pitch=%.1f, Roll=%.1f, State=%s",
                 g_currentPitch, g_currentRoll, stateName);

        lastLogTime = g_currentMillis;
    }
}

// --- Detect Medicine Intake ---
bool detectMedicineIntake() {
    static float initialPitch = 0.0f;
    static float initialRoll = 0.0f;
    static unsigned long movementStartTime = 0;
    static bool initialPositionSet = false;
    static float maxChange = 0.0f;
    static unsigned long lastDetectionTime = 0;

    // Stability tracking for baseline capture/refresh
    static unsigned long stableDurationStart = 0;
    static float lastStablePitch = 0.0f;
    static float lastStableRoll = 0.0f;

    float pitchChange, rollChange, totalChange;

    switch (g_detectionState) {
        case DETECTION_STATE_IDLE:
            // Check if we're in cooldown period
            if (lastDetectionTime > 0 &&
                (g_currentMillis - lastDetectionTime < g_cooldownTime)) {
                break;
            }

            // After cooldown expires, reset for next detection
            if (lastDetectionTime > 0 &&
                (g_currentMillis - lastDetectionTime >= g_cooldownTime)) {
                initialPositionSet = false;
                maxChange = 0.0f;
                logPrint("SENSOR", "Cooldown expired. Ready for next detection.");
                lastDetectionTime = 0;
            }

            // Stability check for capturing or refreshing the baseline
            {
                float diff = sqrt(pow(g_currentPitch - lastStablePitch, 2) + pow(g_currentRoll - lastStableRoll, 2));
                if (diff < 2.0f) { // Consider stable if movement is less than 2 degrees
                    if (stableDurationStart == 0) {
                        stableDurationStart = g_currentMillis;
                    } else if (g_currentMillis - stableDurationStart > 1000) {
                        // Stable for 1 second
                        bool shouldSet = false;

                        if (!initialPositionSet) {
                            shouldSet = true;
                            logPrint("SENSOR", "Initial position set (stable): Pitch=%.2f, Roll=%.2f",
                                     g_currentPitch, g_currentRoll);
                        } else {
                            // Periodically refresh baseline if we are stable in a different position
                            float distFromInitial = sqrt(pow(g_currentPitch - initialPitch, 2) + pow(g_currentRoll - initialRoll, 2));
                            if (distFromInitial > 5.0f) {
                                shouldSet = true;
                                logPrint("SENSOR", "Baseline updated (drifted): Pitch=%.2f, Roll=%.2f",
                                         g_currentPitch, g_currentRoll);
                            }
                        }

                        if (shouldSet) {
                            initialPitch = g_currentPitch;
                            initialRoll = g_currentRoll;
                            maxChange = 0.0f;
                            initialPositionSet = true;
                        }
                        // Keep stableDurationStart to avoid repeated log/updates unless it moves and settles again
                    }
                } else {
                    // Reset stability timer if moving
                    lastStablePitch = g_currentPitch;
                    lastStableRoll = g_currentRoll;
                    stableDurationStart = g_currentMillis;
                }
            }

            // Don't proceed to movement check until initial position is settled
            if (!initialPositionSet) {
                break;
            }

            // Calculate current change from initial position
            pitchChange = fabs(g_currentPitch - initialPitch);
            rollChange = fabs(g_currentRoll - initialRoll);
            totalChange = sqrt(pitchChange * pitchChange + rollChange * rollChange);

            if (totalChange > maxChange) {
                maxChange = totalChange;
            }

            // Check for significant movement
            if (totalChange > g_movementThreshold) {
                logPrint("SENSOR", "Movement detected: %.2f degrees (threshold: %.2f)",
                         totalChange, g_movementThreshold);
                g_detectionState = DETECTION_STATE_MOVING;
                movementStartTime = g_currentMillis;
            }
            break;

        case DETECTION_STATE_MOVING:
            // Calculate current change from initial position
            pitchChange = fabs(g_currentPitch - initialPitch);
            rollChange = fabs(g_currentRoll - initialRoll);
            totalChange = sqrt(pitchChange * pitchChange + rollChange * rollChange);

            if (totalChange > maxChange) {
                maxChange = totalChange;
            }

            // Check if movement has stopped
            if ((g_currentMillis - movementStartTime > MOVEMENT_STABILITY_MS) ||
                (g_currentMillis - movementStartTime > 2000)) {

                logPrint("SENSOR", "Movement completed. Max change: %.2f degrees", maxChange);

                if (maxChange >= g_movementThreshold) {
                    logPrint("SENSOR", "✅ Medicine intake detected! %.2f degrees", maxChange);

                    // Always record intake timestamp (even if time not synced)
                    g_lastIntakeTimestamp = g_timeSynced ? g_currentTimestamp : 0;
                    logPrint("SENSOR", "Intake timestamp recorded: %lu (synced: %s)",
                             g_lastIntakeTimestamp, g_timeSynced ? "yes" : "no");

                    // Green LED feedback for 3 seconds
                    Serial.println("LED: GREEN blinking for 3 seconds (intake detected)");
                    Serial.flush();
                    for (int i = 0; i < 6; i++) {
                        setLedColor(false, true, false);  // Green ON
                        delay(300);
                        setLedColor(false, false, false);  // OFF
                        delay(200);
                    }
                    Serial.println("LED: Returning to normal state");
                    Serial.flush();

                    g_detectionState = DETECTION_STATE_CONFIRMED;

                    // Send BLE notification: INTAKE:<timestamp> (always send)
                    char sensorData[64];
                    snprintf(sensorData, sizeof(sensorData),
                             "INTAKE:%lu", g_lastIntakeTimestamp);
                    sendSensorNotification(sensorData);

                    // Set cooldown
                    lastDetectionTime = g_currentMillis;
                    logPrint("SENSOR", "Cooldown started: %lu seconds", g_cooldownTime / 1000);

                    // Reset for next detection
                    initialPositionSet = false;
                    maxChange = 0.0f;

                    return true;
                } else {
                    logPrint("SENSOR", "Movement insufficient (%.2f < %.2f degrees). Ignoring.",
                             maxChange, g_movementThreshold);
                    g_detectionState = DETECTION_STATE_IDLE;
                    initialPositionSet = false;
                    maxChange = 0.0f;
                }
            }
            break;

        case DETECTION_STATE_CONFIRMED:
            // Reset to idle after confirmation
            g_detectionState = DETECTION_STATE_IDLE;
            initialPositionSet = false;
            maxChange = 0.0f;
            break;
    }

    return false;
}
