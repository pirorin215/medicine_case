package com.pirorin215.medicinecasemob.ui.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MedicineSchedule::class, MedicineIntakeRecord::class, DetectionSettings::class],
    version = 3,
    exportSchema = false
)
abstract class MedicineDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao

    companion object {
        @Volatile
        private var INSTANCE: MedicineDatabase? = null

        private val callback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Insert default detection settings
                db.execSQL(
                    "INSERT INTO detection_settings (id, movementThreshold, cooldownTime) " +
                    "VALUES (1, 70.0, 30000)"
                )
                // Insert default schedules (morning 7:00, afternoon 12:00, evening 19:00)
                db.execSQL("INSERT INTO schedules (id, enabled, hour, minute, taken, takenTimestamp) VALUES (0, 1, 7, 0, 0, 0)")
                db.execSQL("INSERT INTO schedules (id, enabled, hour, minute, taken, takenTimestamp) VALUES (1, 1, 12, 0, 0, 0)")
                db.execSQL("INSERT INTO schedules (id, enabled, hour, minute, taken, takenTimestamp) VALUES (2, 1, 19, 0, 0, 0)")
            }
        }

        fun getDatabase(context: Context): MedicineDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MedicineDatabase::class.java,
                    "medicine_database"
                )
                    .addCallback(callback)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
