package com.adden00.tk_storage_back.dto

data class ItemResponse(val success: Boolean, val equipItem: EquipItemDto? = null)

data class FreeIdResponse(val success: Boolean, val id: String? = null)

data class SearchResponse(val success: Boolean, val items: List<EquipItemDto>? = null)

data class ErrorResponse(val success: Boolean = false, val message: String)
