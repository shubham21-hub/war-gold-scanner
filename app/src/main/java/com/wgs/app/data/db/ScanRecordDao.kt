package com.wgs.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanRecordDao {

    @Query("SELECT * FROM scan_records WHERE warId = :warId ORDER BY baseNumber ASC")
    fun getRecordsByWar(warId: String): Flow<List<ScanRecord>>

    @Query(
        """SELECT * FROM scan_records
           WHERE warId = :warId
           AND (CAST(baseNumber AS TEXT) LIKE '%' || :query || '%'
                OR LOWER(playerName) LIKE '%' || LOWER(:query) || '%')
           ORDER BY baseNumber ASC"""
    )
    fun searchRecords(warId: String, query: String): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scan_records WHERE warId = :warId AND baseNumber = :baseNumber LIMIT 1")
    suspend fun findByBaseNumber(warId: String, baseNumber: Int): ScanRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ScanRecord): Long

    @Update
    suspend fun update(record: ScanRecord)

    @Delete
    suspend fun delete(record: ScanRecord)

    @Query("DELETE FROM scan_records WHERE warId = :warId")
    suspend fun deleteAllForWar(warId: String)

    @Query("SELECT COUNT(*) FROM scan_records WHERE warId = :warId")
    suspend fun countForWar(warId: String): Int
}
