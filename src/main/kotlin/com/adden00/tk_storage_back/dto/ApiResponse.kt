package com.adden00.tk_storage_back.dto

import com.adden00.tk_storage_back.domain.HistoryEntry

data class ItemResponse(val success: Boolean, val equipItem: EquipItemDto? = null)

data class FreeIdResponse(val success: Boolean, val id: String? = null)

data class SearchResponse(val success: Boolean, val items: List<EquipItemDto>? = null)

data class HistoryResponse(val success: Boolean, val entries: List<HistoryEntry>? = null)

data class ErrorResponse(val success: Boolean = false, val message: String)

data class ExportResponse(val success: Boolean, val message: String? = null)
