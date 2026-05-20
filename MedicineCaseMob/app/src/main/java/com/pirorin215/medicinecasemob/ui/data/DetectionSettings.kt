package com.pirorin215.medicinecasemob.ui.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Detection settings for medicine intake
 */
@Entity(tableName = "detection_settings")
data class DetectionSettings(
    @PrimaryKey
    val id: Int = 1,  // Always single record
    val movementThreshold: Float = 70.0f,  // Movement threshold in degrees
    val cooldownTime: Long = 30000L  // Cooldown time in milliseconds
)
