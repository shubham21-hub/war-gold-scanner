package com.wgs.app.data.repository

import com.wgs.app.data.db.ScanRecord
import com.wgs.app.data.db.ScanRecordDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(
    private val dao: ScanRecordDao
) {

    fun getRecordsByWar(warId: String): Flow<List<ScanRecord>> =
        dao.getRecordsByWar(warId)

    fun searchRecords(warId: String, query: String): Flow<List<ScanRecord>> =
        dao.searchRecords(warId, query)

    suspend fun findDuplicate(warId: String, baseNumber: Int): ScanRecord? =
        dao.findByBaseNumber(warId, baseNumber)

    suspend fun saveRecord(record: ScanRecord): Long =
        dao.insert(record)

    suspend fun updateRecord(record: ScanRecord) =
        dao.update(record)

    suspend fun deleteRecord(record: ScanRecord) =
        dao.delete(record)

    suspend fun deleteAllForWar(warId: String) =
        dao.deleteAllForWar(warId)

    suspend fun countForWar(warId: String): Int =
        dao.countForWar(warId)
}
