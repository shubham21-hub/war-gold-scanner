package com.wgs.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_records",
    indices = [Index(value = ["warId", "baseNumber"], unique = true)]
)
data class ScanRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val warId: String,
    val baseNumber: Int,
    val playerName: String,
    val goldValue: Long,
    val date: String,
    val timestamp: Long = System.currentTimeMillis()
)
