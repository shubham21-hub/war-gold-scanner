package com.wgs.app.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wgs.app.data.db.ScanRecord
import com.wgs.app.data.repository.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val repository: ScanRepository
) : ViewModel() {

    private val _warId = MutableStateFlow("war_${java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())}")
    val warId: StateFlow<String> = _warId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val records: StateFlow<List<ScanRecord>> = combine(_warId, _searchQuery) { wid, q -> wid to q }
        .flatMapLatest { (wid, q) ->
            if (q.isBlank()) repository.getRecordsByWar(wid)
            else repository.searchRecords(wid, q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setWarId(id: String) { _warId.value = id }

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun deleteRecord(record: ScanRecord) {
        viewModelScope.launch { repository.deleteRecord(record) }
    }

    fun deleteAll() {
        viewModelScope.launch { repository.deleteAllForWar(_warId.value) }
    }

    fun updateRecord(record: ScanRecord) {
        viewModelScope.launch { repository.updateRecord(record) }
    }
}
