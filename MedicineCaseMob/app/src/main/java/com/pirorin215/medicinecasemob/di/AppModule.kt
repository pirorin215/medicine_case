package com.pirorin215.medicinecasemob.di

import android.content.Context
import com.pirorin215.medicinecasemob.ble.BleManager
import com.pirorin215.medicinecasemob.ui.data.MedicineDatabase
import com.pirorin215.medicinecasemob.ui.data.MedicineDao
import com.pirorin215.medicinecasemob.ui.data.MedicineRepository
import com.pirorin215.medicinecasemob.ui.data.MedicineSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideLogManager(): com.pirorin215.medicinecasemob.util.LogManager {
        return com.pirorin215.medicinecasemob.util.LogManager.getInstance()
    }

    @Provides
    @Singleton
    fun provideMedicineDatabase(
        @ApplicationContext context: Context
    ): MedicineDatabase {
        return MedicineDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideMedicineDao(database: MedicineDatabase): MedicineDao {
        return database.medicineDao()
    }

    @Provides
    @Singleton
    fun provideMedicineRepository(dao: MedicineDao): MedicineRepository {
        return MedicineRepository(dao)
    }

    @Provides
    @Singleton
    fun provideBleManager(
        @ApplicationContext context: Context
    ): BleManager {
        return BleManager(com.pirorin215.medicinecasemob.util.LogManager.getInstance(), context)
    }

    @Provides
    @Singleton
    fun provideMedicineSettingsRepository(
        @ApplicationContext context: Context
    ): MedicineSettingsRepository {
        return MedicineSettingsRepository(context)
    }
}
