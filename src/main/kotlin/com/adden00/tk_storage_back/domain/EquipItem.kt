package com.adden00.tk_storage_back.domain

import com.adden00.tk_storage_back.dto.EquipItemDto
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

fun EquipItemDto.toItem() = EquipItem(
    id = id,
    category = category,
    brand = brand,
    name = name,
    color = color,
    weigh = weigh,
    quality = quality,
    location = location,
    event = event,
    info = info,
    date = date
)
