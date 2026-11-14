package com.adden00.tk_storage_back.dto

data class UpdateItemRequest(
    val newItem: EquipItemDto,
    val historyAction: String = "ОБНОВЛЕНО",
)
