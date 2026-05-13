package com.adden00.tk_storage_back.dto

data class AddItemRequest(
    val newItem: EquipItemDto,
    val keyholderName: String
)
