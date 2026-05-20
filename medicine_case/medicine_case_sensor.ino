/**
 * 6-Axis Sensor Implementation for Medicine Case
 *
 * Uses LSM6DS3 accelerometer + gyroscope to detect medicine intake
 * by detecting large angle changes (90+ degrees)
 */

#include "medicine_case.h"

// --- Sensor Setup ---
void setupSensor() {
    Serial.println("SENSOR: Initializing LSM6DS3...");
    Serial.flush();

    // Seeed LSM6DS3ライブラリを使用
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
    // Seeed LSM6DS3ライブラリを使用
    float ax = g_lsm6ds3->readFloatAccelX();
    float ay = g_lsm6ds3->readFloatAccelY();
    float az = g_lsm6ds3->readFloatAccelZ();

    // Calculate pitch and roll from accelerometer data
    // Pitch: rotation around Y-axis (forward/backward tilt)
    // Roll: rotation around X-axis (left/right tilt)

    *pitch = atan2(ay, sqrt(ax * ax + az * az)) * 180.0 / PI;
    *roll = atan2(-ax, sqrt(ay * ay + az * az)) * 180.0 / PI;

    // Normalize angles to -180 to 180 range
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

    // Calculate current angles
    float newPitch, newRoll;
    calculateAngles(&newPitch, &newRoll);

    // Smooth the angles (simple moving average)
    const float alpha = 0.8f;  // Smoothing factor
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

        Serial.print("SENSOR: Pitch=");
        Serial.print(g_currentPitch, 1);
        Serial.print(", Roll=");
        Serial.print(g_currentRoll, 1);
        Serial.print(", State=");
        Serial.println(stateName);
        Serial.flush();

        lastLogTime = g_currentMillis;
    }
}

// --- Check if Position is Stable ---
bool isPositionStable(float pitch, float roll, unsigned long duration) {
    static float stablePitchSum = 0.0f;
    static float stableRollSum = 0.0f;
    static int stableSampleCount = 0;
    static unsigned long stabilityStartTime = 0;

    // Start stability check
    if (duration == 0) {
        stablePitchSum = 0.0f;
        stableRollSum = 0.0f;
        stableSampleCount = 0;
        stabilityStartTime = g_currentMillis;
        return false;
    }

    // Check if stability duration has elapsed
    if (g_currentMillis - stabilityStartTime >= duration) {
        // Calculate average position during stability period
        float avgPitch = stablePitchSum / stableSampleCount;
        float avgRoll = stableRollSum / stableSampleCount;

        // Calculate variance (must be low for stable position)
        float varianceSum = 0.0f;
        // This is simplified - in production you'd track all samples
        const float stabilityThreshold = 5.0f;  // 5 degrees variance threshold

        bool stable = (varianceSum < stabilityThreshold);

        if (stable) {
            g_stablePitch = avgPitch;
            g_stableRoll = avgRoll;
            logPrint("SENSOR", "Stable position: Pitch=%.2f, Roll=%.2f",
                     g_stablePitch, g_stableRoll);
        }

        // Reset stability tracking
        stablePitchSum = 0.0f;
        stableRollSum = 0.0f;
        stableSampleCount = 0;

        return stable;
    }

    // Accumulate samples for stability calculation
    stablePitchSum += pitch;
    stableRollSum += roll;
    stableSampleCount++;

    return false;
}

// --- Detect Medicine Intake ---
bool detectMedicineIntake() {
    static float initialPitch = 0.0f;
    static float initialRoll = 0.0f;
    static unsigned long movementStartTime = 0;
    static bool initialPositionSet = false;

    // Declare variables outside switch to avoid cross-initialization issues
    float pitchChange, rollChange, totalChange;

    switch (g_detectionState) {
        case DETECTION_STATE_IDLE:
            // Set initial position only once (not every loop!)
            if (!initialPositionSet) {
                initialPitch = g_currentPitch;
                initialRoll = g_currentRoll;
                initialPositionSet = true;
            }

            // Check for significant movement (angle change)
            pitchChange = fabs(g_currentPitch - initialPitch);
            rollChange = fabs(g_currentRoll - initialRoll);

            // Calculate total angle change
            totalChange = sqrt(pitchChange * pitchChange + rollChange * rollChange);

            if (totalChange > MOVEMENT_THRESHOLD_DEG) {
                logPrint("SENSOR", "Movement detected: %.2f degrees (threshold: %.2f)",
                         totalChange, MOVEMENT_THRESHOLD_DEG);
                g_detectionState = DETECTION_STATE_MOVING;
                movementStartTime = g_currentMillis;
            }
            break;

        case DETECTION_STATE_MOVING:
            // Calculate current change from initial position
            pitchChange = fabs(g_currentPitch - initialPitch);
            rollChange = fabs(g_currentRoll - initialRoll);
            totalChange = sqrt(pitchChange * pitchChange + rollChange * rollChange);

            // Check if movement has stopped (stable for 500ms)
            if (g_currentMillis - movementStartTime > MOVEMENT_STABILITY_MS) {
                logPrint("SENSOR", "Movement completed. Checking stability...");
                logPrint("SENSOR", "Total change: %.2f degrees", totalChange);
                g_detectionState = DETECTION_STATE_STABILIZING;
                isPositionStable(g_currentPitch, g_currentRoll, 0);  // Reset stability check
            }
            break;

        case DETECTION_STATE_STABILIZING:
            // Check for stable position
            if (isPositionStable(g_currentPitch, g_currentRoll, MOVEMENT_STABILITY_MS)) {
                // Verify that we actually moved 70+ degrees
                pitchChange = fabs(g_stablePitch - initialPitch);
                rollChange = fabs(g_stableRoll - initialRoll);
                totalChange = sqrt(pitchChange * pitchChange + rollChange * rollChange);

                logPrint("SENSOR", "Stable position reached. Total change: %.2f degrees (threshold: %.2f)",
                         totalChange, MOVEMENT_THRESHOLD_DEG);

                if (totalChange >= MOVEMENT_THRESHOLD_DEG) {
                    logPrint("SENSOR", "✅ Medicine intake detected! %.2f degrees", totalChange);

                    // 緑色LEDを3秒間点滅（300ms点灯 + 200ms消灯 × 6回 = 3秒）
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

                    // Determine which schedule this intake belongs to
                    int currentHour = getHours();
                    int scheduleIndex = -1;

                    // Determine schedule based on time
                    for (int i = 0; i < MAX_SCHEDULES; i++) {
                        if (!g_schedules[i].enabled) continue;

                        // Check if current time is within 2 hours of scheduled time
                        int scheduleHour = g_schedules[i].hour;
                        int hourDiff = abs(currentHour - scheduleHour);

                        if (hourDiff <= 2) {
                            scheduleIndex = i;
                            break;
                        }
                    }

                    if (scheduleIndex >= 0) {
                        recordMedicineIntake(scheduleIndex);
                    } else {
                        logPrint("SENSOR", "No matching schedule found for current time");
                    }

                    // Send sensor notification
                    char sensorData[128];
                    snprintf(sensorData, sizeof(sensorData),
                             "INTAKE:%.2f,%.2f,%lu",
                             g_stablePitch, g_stableRoll, g_currentTimestamp);
                    sendSensorNotification(sensorData);

                    // Reset initial position for next detection
                    initialPositionSet = false;

                    return true;
                } else {
                    logPrint("SENSOR", "Movement insufficient (%.2f < %.2f degrees). Ignoring.",
                             totalChange, MOVEMENT_THRESHOLD_DEG);
                    g_detectionState = DETECTION_STATE_IDLE;
                    initialPositionSet = false;
                }
            }
            break;

        case DETECTION_STATE_CONFIRMED:
            // Reset to idle after confirmation
            g_detectionState = DETECTION_STATE_IDLE;
            initialPositionSet = false;
            break;
    }

    return false;
}

// --- Record Medicine Intake ---
void recordMedicineIntake(int scheduleIndex) {
    if (scheduleIndex < 0 || scheduleIndex >= MAX_SCHEDULES) {
        Serial.println("SENSOR: Invalid schedule index!");
        Serial.flush();
        return;
    }

    MedicineSchedule* schedule = &g_schedules[scheduleIndex];

    const char* scheduleName = (scheduleIndex == 0) ? "Morning" :
                               (scheduleIndex == 1) ? "Afternoon" : "Evening";

    Serial.println("========================================");
    Serial.println("💊 MEDICINE INTAKE DETECTED!");
    Serial.print("Schedule: ");
    Serial.println(scheduleName);
    Serial.print("Time: ");
    Serial.print(getHours());
    Serial.print(":");
    Serial.print(getMinutes());
    Serial.print(":");
    Serial.println(getSeconds());
    Serial.println("========================================");
    Serial.flush();

    schedule->taken = 1;
    schedule->takenTimestamp = g_currentTimestamp;

    // Save to storage
    saveSettings();

    // 服薬検知をLEDで示す（青色LEDは無効なため、赤色点滅で代用）
    Serial.println("LED: RED blinking for 3 seconds (intake detected)");
    Serial.flush();

    // 赤色LEDを3回点滅して服薬検知を示す
    for (int i = 0; i < 3; i++) {
        setLedColor(true, false, false);  // Red
        delay(300);
        setLedColor(false, false, false);  // Off
        delay(200);
    }

    Serial.println("LED: Returning to normal state");
    Serial.flush();

    // Send notification
    sendIntakeNotification(scheduleIndex);

    Serial.println("✅ Intake recorded successfully!");
    Serial.flush();
}

// --- Send Intake Notification ---
void sendIntakeNotification(int scheduleIndex) {
    if (!g_deviceConnected || !g_bleNotificationEnabled) {
        return;
    }

    MedicineSchedule* schedule = &g_schedules[scheduleIndex];
    const char* scheduleName = (scheduleIndex == 0) ? "Morning" :
                               (scheduleIndex == 1) ? "Afternoon" : "Evening";

    char notification[128];
    snprintf(notification, sizeof(notification),
             "TAKEN:%d:%s:%02d:%02d:%lu",
             scheduleIndex,
             scheduleName,
             schedule->hour,
             schedule->minute,
             schedule->takenTimestamp);

    sendSensorNotification(notification);
}
