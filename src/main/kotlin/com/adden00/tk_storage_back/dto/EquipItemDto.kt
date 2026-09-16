package com.adden00.tk_storage_back.dto

data class EquipItemDto(
    val id: String,
    val category: String = "",
    val brand: String = "",
    val name: String = "",
    val color: String = "",
    val weigh: String = "",
    val quality: String = "",
    val location: String = "",
    val event: String = "",
    val info: String = "",
    val date: String = "",
    /**
     * Привязка к записи справочника. В ответах пустая строка — привязки нет.
     * В запросе null означает "поле не прислано", пустая строка —
     * "привязка снята"; см. EquipService.resolveLocation.
     */
    val locationUserId: String? = null
)
