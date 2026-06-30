package com.wgs.app.di

import android.content.Context
import androidx.room.Room
import com.wgs.app.data.db.ScanRecordDao
import com.wgs.app.data.db.WGSDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): WGSDatabase =
        Room.databaseBuilder(context, WGSDatabase::class.java, "wgs_database.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideScanRecordDao(db: WGSDatabase): ScanRecordDao = db.scanRecordDao()
}
