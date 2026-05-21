package com.pirorin215.medicinecasemob.ui.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MedicineSchedule::class, MedicineIntakeRecord::class, DetectionSettings::class],
    version = 5,
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
                // Insert default schedules with time ranges
                db.execSQL("INSERT INTO schedules (id, enabled, startHour, startMinute, endHour, endMinute) VALUES (0, 1, 8, 0, 11, 0)")
                db.execSQL("INSERT INTO schedules (id, enabled, startHour, startMinute, endHour, endMinute) VALUES (1, 1, 12, 0, 17, 0)")
                db.execSQL("INSERT INTO schedules (id, enabled, startHour, startMinute, endHour, endMinute) VALUES (2, 1, 19, 0, 22, 0)")
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
