package com.adden00.tk_storage_back.repository

import com.adden00.tk_storage_back.domain.HistoryEntry
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.MongoRepository

interface HistoryEntryRepository : MongoRepository<HistoryEntry, String> {
    fun findAllByIdIs(id: String): List<HistoryEntry>

    fun findAllBy(pageable: Pageable): Slice<HistoryEntry>

    fun findAllByMongoIdLessThan(mongoId: String, pageable: Pageable): Slice<HistoryEntry>

    fun findAllByUserIds(userId: String, pageable: Pageable): Slice<HistoryEntry>

    fun findAllByUserIdsAndMongoIdLessThan(userId: String, mongoId: String, pageable: Pageable): Slice<HistoryEntry>
}
