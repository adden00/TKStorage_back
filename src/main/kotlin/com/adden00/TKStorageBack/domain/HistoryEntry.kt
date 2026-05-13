package com.adden00.TKStorageBack.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "history")
data class HistoryEntry(
    @Id val mongoId: String? = null,
    val action: String,
    val id: String,
    val category: String,
    val brand: String,
    val name: String,
    val color: String,
    val weigh: String,
    val quality: String,
    val location: String,
    val event: String,
    val info: String,
    val timestamp: String,
    val keyholderName: String
)
