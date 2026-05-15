package com.adden00.tk_storage_back.repository

import com.adden00.tk_storage_back.domain.HistoryEntry
import org.springframework.data.mongodb.repository.MongoRepository

interface HistoryEntryRepository : MongoRepository<HistoryEntry, String> {
    fun findAllByIdIs(id: String): List<HistoryEntry>
}
