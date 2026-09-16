package com.adden00.tk_storage_back.service

import com.adden00.tk_storage_back.domain.ClubUser
import com.adden00.tk_storage_back.domain.EquipItem
import com.adden00.tk_storage_back.domain.HistoryEntry
import com.adden00.tk_storage_back.domain.toItem
import com.adden00.tk_storage_back.dto.*
import com.adden00.tk_storage_back.repository.ClubUserRepository
import com.adden00.tk_storage_back.repository.EquipItemRepository
import com.adden00.tk_storage_back.repository.HistoryEntryRepository
import com.adden00.tk_storage_back.util.Csv
import com.adden00.tk_storage_back.util.Normalize
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Итог сверки со справочником: снятые протухшие ссылки и поправленный текст. */
data class DirectoryCheckResult(val cleared: Int = 0, val renamed: Int = 0)

@Service
class EquipService(
    private val equipItemRepository: EquipItemRepository,
    private val historyEntryRepository: HistoryEntryRepository,
    private val clubUserRepository: ClubUserRepository,
    private val appScriptRestClient: RestClient,
    @Value("\${appscript.export.url}") private val appScriptUrl: String,
    @Value("\${appscript.import.url}") private val appScriptImportUrl: String
) {

    companion object {
        private const val HEADER =
            "id,category,brand,name,color,weigh,quality,location,event,info,date,locationUserId"
        private const val COLUMN_COUNT = 12

        /** Выгрузки до появления колонки с привязкой. */
        private const val LEGACY_COLUMN_COUNT = 11
        private const val USER_NOT_FOUND = "Пользователь не найден, обновите справочник"

        /**
         * Единственное, что проставляется автоматически: служебные места.
         * Это не догадка — соответствие задано явно, людей оно не касается.
         * Ключи уже нормализованы (нижний регистр, схлопнутые пробелы).
         */
        private val SYSTEM_PLACES = mapOf(
            "склад" to ClubUser.ID_WAREHOUSE,
            "новый склад" to ClubUser.ID_UNKNOWN,
            "неизвестно" to ClubUser.ID_UNKNOWN
        )
    }

    fun getItem(id: String): ItemResponse {
        val item = equipItemRepository.findByAppId(id) ?: return ItemResponse(success = false)
        return ItemResponse(success = true, equipItem = buildDto(item))
    }

    fun updateItem(id: String, body: UpdateItemRequest): ItemResponse {
        val item = equipItemRepository.findByAppId(id) ?: return ItemResponse(success = false)
        val newItem = resolveLocation(body.newItem, item)
            ?: return ItemResponse(success = false, message = USER_NOT_FOUND)

        historyEntryRepository.save(
            HistoryEntry(
                action = body.historyAction,
                id = sumIfDifferent(item.id, newItem.id),
                category = sumIfDifferent(item.category, newItem.category),
                brand = sumIfDifferent(item.brand, newItem.brand),
                name = sumIfDifferent(item.name, newItem.name),
                color = sumIfDifferent(item.color, newItem.color),
                weigh = sumIfDifferent(item.weigh, newItem.weigh),
                quality = sumIfDifferent(item.quality, newItem.quality),
                location = sumIfDifferent(item.location, newItem.location),
                event = sumIfDifferent(item.event, newItem.event),
                info = sumIfDifferent(item.info, newItem.info),
                timestamp = getCurrentTime(),
                keyholderName = body.keyholderName
            )
        )

        val updated = item.copy(
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
            date = newItem.date,
            locationUserId = newItem.locationUserId ?: ""
        )
        equipItemRepository.save(updated)
        return ItemResponse(success = true, equipItem = buildDto(updated))
    }

    fun addItem(body: AddItemRequest): ItemResponse {
        val newItem = resolveLocation(body.newItem, null)
            ?: return ItemResponse(success = false, message = USER_NOT_FOUND)
        return try {
            equipItemRepository.save(newItem.toItem())
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
                    keyholderName = body.keyholderName
                )
            )
            ItemResponse(success = true, equipItem = newItem)
        } catch (_: org.springframework.dao.DuplicateKeyException) {
            ItemResponse(success = false)
        }
    }

    fun getFreeId(): FreeIdResponse {
        val maxId = equipItemRepository.findMaxNumericId()?.maxId
            ?: return FreeIdResponse(success = false)
        return FreeIdResponse(success = true, id = (maxId + 1).toString())
    }

    fun getAllItems(): SearchResponse {
        val items = equipItemRepository.findAll().map { buildDto(it) }
        return SearchResponse(success = true, items = items)
    }

    /**
     * Сверяет привязки со свежим справочником. Ничего не угадывает: новые совпадения
     * по тексту сознательно не ищутся — привязку ставит только человек через приложение.
     *
     * Делает ровно две вещи: снимает ссылки на записи, которых больше нет (обычно
     * ФИО поправили в таблице и у человека сменился id), и держит текст привязанных
     * предметов равным каноническому ФИО.
     */
    fun checkAgainstDirectory(): DirectoryCheckResult {
        val allUsers = clubUserRepository.findAll()
        if (allUsers.isEmpty()) return DirectoryCheckResult()
        val usersById = allUsers.associateBy { it.id }

        var cleared = 0
        var renamed = 0
        val changed = mutableListOf<EquipItem>()

        for (item in equipItemRepository.findAll()) {
            if (item.locationUserId.isBlank()) continue
            val user = usersById[item.locationUserId]
            when {
                user == null -> {
                    // текст ФИО остаётся, чтобы было видно, кого перепривязать руками
                    changed.add(item.copy(locationUserId = ""))
                    cleared++
                }
                item.location != user.fullName -> {
                    changed.add(item.copy(location = user.fullName))
                    renamed++
                }
            }
        }

        if (changed.isNotEmpty()) equipItemRepository.saveAll(changed)
        return DirectoryCheckResult(cleared = cleared, renamed = renamed)
    }

    /** Предметы, местоположение которых осталось свободным текстом. */
    fun getUnboundItems(): SearchResponse {
        val items = equipItemRepository.findAll()
            .filter { it.locationUserId.isBlank() && it.location.isNotBlank() }
            .map { buildDto(it) }
        return SearchResponse(success = true, items = items)
    }

    /** Предметы, привязанные к записи справочника: что у человека на руках. */
    fun getItemsOfUser(userId: String): SearchResponse {
        if (userId.isBlank()) return SearchResponse(success = true, items = emptyList())
        val items = equipItemRepository.findAllByLocationUserId(userId)
            .sortedBy { it.id.toLongOrNull() ?: Long.MAX_VALUE }
            .map { buildDto(it) }
        return SearchResponse(success = true, items = items)
    }

    fun search(query: String): SearchResponse {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return SearchResponse(success = true, items = emptyList())
        val items = equipItemRepository.searchAcrossFields(words).map { buildDto(it) }
        return SearchResponse(success = true, items = items)
    }

    fun getItemHistory(id: String): HistoryResponse {
        val entries = historyEntryRepository.findAllByIdIs(id).map { e ->
            e.copy(
                id = if ("-->" in e.id) e.id else "",
                category = if ("-->" in e.category) e.category else "",
                brand = if ("-->" in e.brand) e.brand else "",
                name = if ("-->" in e.name) e.name else "",
                color = if ("-->" in e.color) e.color else "",
                weigh = if ("-->" in e.weigh) e.weigh else "",
                quality = if ("-->" in e.quality) e.quality else "",
                location = if ("-->" in e.location) e.location else "",
                event = if ("-->" in e.event) e.event else "",
                info = if ("-->" in e.info) e.info else "",
            )
        }
        return HistoryResponse(success = true, entries = entries)
    }

    fun exportToSheets(): ExportResponse {
        if (appScriptUrl.isBlank()) return ExportResponse(success = false, message = "Apps Script URL not configured")
        return try {
            appScriptRestClient.post()
                .uri(appScriptUrl)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(buildItemsCsv(includeHeader = false))
                .retrieve()
                .toBodilessEntity()
            ExportResponse(success = true)
        } catch (e: Exception) {
            ExportResponse(success = false, message = e.message)
        }
    }

    fun buildItemsCsv(includeHeader: Boolean = true): String {
        val rows = equipItemRepository.findAll().sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }.map { item ->
            itemValues(item).joinToString(",") { Csv.escape(it) }
        }
        return (if (includeHeader) listOf(HEADER) + rows else rows).joinToString("\r\n")
    }

    fun importFromSheets(): ImportResponse {
        if (appScriptImportUrl.isBlank()) return ImportResponse(success = false, message = "Apps Script import URL not configured")
        return try {
            // читаем байтами и декодируем сами: без charset в Content-Type
            // Spring взял бы ISO-8859-1 и молча испортил кириллицу
            val csv = appScriptRestClient.get()
                .uri(appScriptImportUrl)
                .retrieve()
                .body(ByteArray::class.java)
                ?.toString(Charsets.UTF_8)
                ?: return ImportResponse(success = false, message = "Empty response from Apps Script")

            val rows = Csv.parse(csv).filter { it.isNotEmpty() && it.any { c -> c.isNotBlank() } }
            if (rows.isEmpty()) return ImportResponse(success = false, message = "CSV has no data rows")
            val width = rows.first().size
            if (width != COLUMN_COUNT && width != LEGACY_COLUMN_COUNT)
                return ImportResponse(success = false, message = "Malformed CSV: wrong column count")
            if (rows.any { it.size != width })
                return ImportResponse(success = false, message = "Malformed CSV: wrong column count")

            // В выгрузке без колонки привязок опереться можно только на то, что уже в базе:
            // привязка держится, пока текст местоположения в таблице не трогали.
            val previous = if (width == LEGACY_COLUMN_COUNT) {
                equipItemRepository.findAll()
                    .filter { it.locationUserId.isNotBlank() }
                    .associate { it.id to (Normalize.text(it.location) to it.locationUserId) }
            } else {
                emptyMap()
            }

            val usersById = clubUserRepository.findAll().associateBy { it.id }

            var bound = 0
            var places = 0
            var dropped = 0
            val items = rows.map { r ->
                val fromSheet = if (width == COLUMN_COUNT) r[11].trim() else ""
                val restored = previous[r[0]]
                    ?.takeIf { it.first == Normalize.text(r[7]) }
                    ?.second
                    .orEmpty()
                val place = SYSTEM_PLACES[Normalize.text(r[7])].orEmpty()
                val candidate = fromSheet.ifBlank { restored }.ifBlank { place }
                val locationUserId = when {
                    candidate.isBlank() -> ""
                    !usersById.containsKey(candidate) -> "".also { dropped++ }
                    else -> candidate.also { if (candidate == place && fromSheet.isBlank() && restored.isBlank()) places++ else bound++ }
                }
                // у служебных мест текст приводим к каноническому: "новый склад" -> "Неизвестно"
                val location = if (locationUserId.isNotBlank() && locationUserId == place) {
                    usersById.getValue(locationUserId).fullName
                } else {
                    r[7]
                }
                EquipItem(id = r[0], category = r[1], brand = r[2], name = r[3], color = r[4],
                    weigh = r[5], quality = r[6], location = location, event = r[8], info = r[9], date = r[10],
                    locationUserId = locationUserId)
            }
            val dupId = items.groupingBy { it.id }.eachCount().entries.firstOrNull { it.value > 1 }?.key
            if (dupId != null) return ImportResponse(success = false, message = "Duplicate id in CSV: $dupId")

            equipItemRepository.deleteAll()
            equipItemRepository.saveAll(items)
            ImportResponse(
                success = true,
                importedCount = items.size,
                message = "привязок: $bound, служебных мест: $places, отброшено неизвестных: $dropped"
            )
        } catch (e: Exception) {
            ImportResponse(success = false, message = e.message)
        }
    }

    fun buildItemsXlsx(): ByteArray {
        val header = listOf(
            "id", "category", "brand", "name", "color", "weigh", "quality",
            "location", "event", "info", "date", "locationUserId"
        )
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Items")
            sheet.createRow(0).let { row ->
                header.forEachIndexed { i, title -> row.createCell(i).setCellValue(title) }
            }
            equipItemRepository.findAll().forEachIndexed { rowIndex, item ->
                val row = sheet.createRow(rowIndex + 1)
                itemValues(item).forEachIndexed { i, value -> row.createCell(i).setCellValue(value) }
            }
            ByteArrayOutputStream().use { out ->
                workbook.write(out)
                return out.toByteArray()
            }
        }
    }

    private fun itemValues(item: EquipItem) = listOf(
        item.id, item.category, item.brand, item.name, item.color,
        item.weigh, item.quality, item.location, item.event, item.info, item.date,
        item.locationUserId
    )

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
        date = item.date,
        locationUserId = item.locationUserId
    )

    /**
     * Приводит местоположение к консистентному виду.
     * Возвращает null, если запрошена привязка к несуществующему пользователю.
     */
    private fun resolveLocation(dto: EquipItemDto, current: EquipItem?): EquipItemDto? {
        val requested = dto.locationUserId
        return when {
            // выбрали запись справочника: location всегда берём оттуда,
            // чтобы текст и ссылка не разъезжались
            !requested.isNullOrBlank() -> {
                val user = clubUserRepository.findByAppId(requested) ?: return null
                dto.copy(location = user.fullName, locationUserId = user.id)
            }
            // привязку сняли явно: location остаётся свободным текстом
            requested != null -> dto.copy(locationUserId = "")
            // поле не прислано вовсе (клиент не знает про привязки): сохраняем её,
            // только пока текст не тронули, иначе location и привязка разойдутся
            else -> {
                // сравниваем нормализованно: "склад" и "Склад" — это не смена местоположения
                val untouched = current
                    ?.takeIf { Normalize.text(dto.location) == Normalize.text(it.location) }
                if (untouched != null) {
                    // текст тот же с точностью до регистра — держим канонический вариант
                    dto.copy(location = untouched.location, locationUserId = untouched.locationUserId)
                } else {
                    dto.copy(locationUserId = "")
                }
            }
        }
    }

    private fun sumIfDifferent(old: String, new: String) =
        if (old == new) old else "$old-->$new"

    private fun getCurrentTime(): String =
        ZonedDateTime.now(ZoneId.of("GMT+3"))
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
}
