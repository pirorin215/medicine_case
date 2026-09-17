package com.pirorin215.medicinecasemob.di

import android.content.Context
import com.pirorin215.medicinecasemob.ui.data.MedicineDao
import com.pirorin215.medicinecasemob.ui.data.MedicineDatabase
import com.pirorin215.medicinecasemob.ui.data.MedicineRepository
import com.pirorin215.medicinecasemob.ui.data.PreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// LogManager / BleManager / PreferenceManager は @Inject @Singleton コンストラクタを持つため
// ここでの @Provides は不要。Database/Dao 系のみ提供する。
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
    fun provideMedicineRepository(
        dao: MedicineDao,
        preferenceManager: PreferenceManager
    ): MedicineRepository {
        return MedicineRepository(dao, preferenceManager)
    }
}
