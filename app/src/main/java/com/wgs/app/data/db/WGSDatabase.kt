package com.wgs.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ScanRecord::class],
    version = 1,
    exportSchema = false
)
abstract class WGSDatabase : RoomDatabase() {
    abstract fun scanRecordDao(): ScanRecordDao
}
