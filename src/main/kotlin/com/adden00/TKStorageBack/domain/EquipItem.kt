package com.adden00.TKStorageBack.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "equip_items")
data class EquipItem(
    @Id val mongoId: String? = null,
    @Indexed(unique = true) val id: String,
    val category: String = "",
    val brand: String = "",
    val name: String = "",
    val color: String = "",
    val weigh: String = "",
    val quality: String = "",
    val location: String = "",
    val event: String = "",
    val info: String = "",
    val date: String = ""
)
