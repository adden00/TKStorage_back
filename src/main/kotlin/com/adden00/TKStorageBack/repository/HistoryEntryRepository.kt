package com.adden00.TKStorageBack.repository

import com.adden00.TKStorageBack.domain.HistoryEntry
import org.springframework.data.mongodb.repository.MongoRepository

interface HistoryEntryRepository : MongoRepository<HistoryEntry, String>
