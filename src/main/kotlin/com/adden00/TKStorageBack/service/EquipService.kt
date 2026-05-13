package com.adden00.TKStorageBack.service

import com.adden00.TKStorageBack.domain.EquipItem
import com.adden00.TKStorageBack.domain.HistoryEntry
import com.adden00.TKStorageBack.dto.*
import com.adden00.TKStorageBack.repository.EquipItemRepository
import com.adden00.TKStorageBack.repository.HistoryEntryRepository
import org.springframework.stereotype.Service
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Service
class EquipService(
    private val equipItemRepository: EquipItemRepository,
    private val historyEntryRepository: HistoryEntryRepository
) {

    fun getItem(id: String): ItemResponse {
        val item = equipItemRepository.findByAppId(id) ?: return ItemResponse(success = false)
        return ItemResponse(success = true, equipItem = buildDto(item))
    }

    fun updateItem(params: ItemRequestParams): ItemResponse {
        val item = equipItemRepository.findByAppId(params.id ?: return ItemResponse(success = false))
            ?: return ItemResponse(success = false)

        historyEntryRepository.save(
            HistoryEntry(
                action = "ОБНОВЛЕНО",
                id = sumIfDifferent(item.id, params.itemId ?: ""),
                category = sumIfDifferent(item.category, params.category ?: ""),
                brand = sumIfDifferent(item.brand, params.brand ?: ""),
                name = sumIfDifferent(item.name, params.name ?: ""),
                color = sumIfDifferent(item.color, params.color ?: ""),
                weigh = sumIfDifferent(item.weigh, params.weigh ?: ""),
                quality = sumIfDifferent(item.quality, params.quality ?: ""),
                location = sumIfDifferent(item.location, params.location ?: ""),
                event = sumIfDifferent(item.event, params.event ?: ""),
                info = sumIfDifferent(item.info, params.info ?: ""),
                timestamp = getCurrentTime(),
                keyholderName = params.keyholderName ?: ""
            )
        )

        val updated = item.copy(
            id = params.itemId ?: item.id,
            category = params.category ?: item.category,
            brand = params.brand ?: item.brand,
            name = params.name ?: item.name,
            color = params.color ?: item.color,
            weigh = params.weigh ?: item.weigh,
            quality = params.quality ?: item.quality,
            location = params.location ?: item.location,
            event = params.event ?: item.event,
            info = params.info ?: item.info,
            date = params.date ?: item.date
        )
        equipItemRepository.save(updated)
        return ItemResponse(success = true, equipItem = buildDto(updated))
    }

    fun addItem(params: ItemRequestParams): ItemResponse {
        val id = params.id
        if (id.isNullOrBlank()) return ItemResponse(success = false)
        if (equipItemRepository.findByAppId(id) != null) return ItemResponse(success = false)

        val newItem = EquipItem(
            id = params.itemId ?: id,
            category = params.category ?: "",
            brand = params.brand ?: "",
            name = params.name ?: "",
            color = params.color ?: "",
            weigh = params.weigh ?: "",
            quality = params.quality ?: "",
            location = params.location ?: "",
            event = params.event ?: "",
            info = params.info ?: "",
            date = params.date ?: ""
        )
        equipItemRepository.save(newItem)

        historyEntryRepository.save(
            HistoryEntry(
                action = "ДОБАВЛЕНО",
                id = newItem.id,
                category = newItem.category,
                brand = newItem.brand,
                name = newItem.name,
                color = newItem.color,
                weigh = newItem.weigh,
                quality = newItem.quality,
                location = newItem.location,
                event = newItem.event,
                info = newItem.info,
                timestamp = getCurrentTime(),
                keyholderName = params.keyholderName ?: ""
            )
        )
        return ItemResponse(success = true, equipItem = buildDto(newItem))
    }

    fun getFreeId(): FreeIdResponse {
        val maxId = equipItemRepository.findAll()
            .mapNotNull { it.id.toLongOrNull() }
            .maxOrNull()
            ?: return FreeIdResponse(success = false)
        return FreeIdResponse(success = true, id = (maxId + 1).toString())
    }

    fun search(query: String): SearchResponse {
        val items = equipItemRepository.findByLocationContainingIgnoreCase(query).map { buildDto(it) }
        return SearchResponse(success = true, items = items)
    }

    private fun buildDto(item: EquipItem) = EquipItemDto(
        id = item.id,
        category = item.category,
        brand = item.brand,
        name = item.name,
        color = item.color,
        weigh = item.weigh,
        quality = item.quality,
        location = item.location,
        event = item.event,
        info = item.info,
        date = item.date
    )

    private fun sumIfDifferent(old: String, new: String) =
        if (old == new) old else "$old-->\n  ->$new"

    private fun getCurrentTime(): String =
        ZonedDateTime.now(ZoneId.of("GMT+3"))
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
}
