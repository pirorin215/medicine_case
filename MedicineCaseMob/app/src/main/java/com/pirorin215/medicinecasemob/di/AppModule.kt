package com.pirorin215.medicinecasemob.di

import android.content.Context
import com.pirorin215.medicinecasemob.ble.BleManager
import com.pirorin215.medicinecasemob.ui.data.MedicineDatabase
import com.pirorin215.medicinecasemob.ui.data.MedicineDao
import com.pirorin215.medicinecasemob.ui.data.MedicineRepository
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
    fun providePreferenceManager(
        @ApplicationContext context: Context
    ): com.pirorin215.medicinecasemob.ui.data.PreferenceManager {
        return com.pirorin215.medicinecasemob.ui.data.PreferenceManager(context)
    }

    @Provides
    @Singleton
    fun provideMedicineRepository(
        dao: MedicineDao,
        preferenceManager: com.pirorin215.medicinecasemob.ui.data.PreferenceManager
    ): MedicineRepository {
        return MedicineRepository(dao, preferenceManager)
    }

    @Provides
    @Singleton
    fun provideBleManager(
        @ApplicationContext context: Context
    ): BleManager {
        return BleManager(com.pirorin215.medicinecasemob.util.LogManager.getInstance(), context)
    }
}
