package com.adden00.tk_storage_back.service

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

/** Итог перепривязки: сколько предметов нашли хозяина и сколько потеряли протухшую ссылку. */
data class RebindResult(val bound: Int = 0, val cleared: Int = 0, val renamed: Int = 0)

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
        private const val HEADER = "id,category,brand,name,color,weigh,quality,location,event,info,date"
        private const val COLUMN_COUNT = 11
        private const val USER_NOT_FOUND = "Пользователь не найден, обновите справочник"
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
     * Переразрешает привязки по свежему справочнику. Вызывается после его синхронизации:
     * это единственный момент, когда текст, раньше ни с кем не совпадавший, может совпасть,
     * а привязка на исправленное ФИО — наоборот, протухнуть.
     *
     * Трогает только проблемные предметы: без привязки и с привязкой на исчезнувшую запись.
     * Здоровые не пересматриваются, иначе правка справочника молча переносила бы вещи.
     */
    fun rebindByDirectory(): RebindResult {
        val allUsers = clubUserRepository.findAll()
        if (allUsers.isEmpty()) return RebindResult()
        val usersById = allUsers.associateBy { it.id }
        val usersByName = allUsers.groupBy { Normalize.text(it.fullName) }
        val nameWordsCache = allUsers.associate { it.id to LocationMatcher.nameWords(it) }
        val matchCache = HashMap<String, String?>()

        var bound = 0
        var cleared = 0
        var renamed = 0
        val changed = mutableListOf<EquipItem>()

        for (item in equipItemRepository.findAll()) {
            val boundUser = usersById[item.locationUserId]
            if (boundUser != null) {
                // привязка жива: следим только за тем, чтобы текст оставался каноническим ФИО
                if (item.location != boundUser.fullName) {
                    changed.add(item.copy(location = boundUser.fullName))
                    renamed++
                }
                continue
            }
            val dangling = item.locationUserId.isNotBlank()
            if (item.location.isBlank()) {
                if (dangling) { changed.add(item.copy(locationUserId = "")); cleared++ }
                continue
            }

            val key = Normalize.text(item.location)
            val matched = if (matchCache.containsKey(key)) {
                matchCache[key]
            } else {
                LocationMatcher.match(item.location, usersByName, allUsers, nameWordsCache)
                    .also { matchCache[key] = it }
            }

            when {
                matched != null -> {
                    val user = usersById.getValue(matched)
                    if (item.locationUserId != matched || item.location != user.fullName) {
                        changed.add(item.copy(location = user.fullName, locationUserId = matched))
                        bound++
                    }
                }
                dangling -> { changed.add(item.copy(locationUserId = "")); cleared++ }
            }
        }

        if (changed.isNotEmpty()) equipItemRepository.saveAll(changed)
        return RebindResult(bound = bound, cleared = cleared, renamed = renamed)
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
            if (rows.any { it.size != COLUMN_COUNT })
                return ImportResponse(success = false, message = "Malformed CSV: wrong column count")

            // CSV снаряжения не содержит привязок, поэтому deleteAll их бы уничтожил.
            // Снимаем их заранее и возвращаем тем предметам, у которых текст не поменяли.
            val bindings = equipItemRepository.findAll()
                .filter { it.locationUserId.isNotBlank() }
                .associate { it.id to (it.location.trim() to it.locationUserId) }

            val allUsers = clubUserRepository.findAll()
            val usersByName = allUsers.groupBy { Normalize.text(it.fullName) }
            val usersById = allUsers.associateBy { it.id }
            val nameWordsCache = allUsers.associate { it.id to LocationMatcher.nameWords(it) }
            val matchCache = HashMap<String, String?>()

            var restored = 0
            var healed = 0
            val items = rows.map { r ->
                val csvLocation = r[7].trim()
                var boundUserId = bindings[r[0]]
                    ?.takeIf { Normalize.text(it.first) == Normalize.text(csvLocation) }
                    ?.second
                    ?.also { restored++ }
                    ?: ""
                // текст в таблице пишут как придётся — пробуем узнать в нём человека
                if (boundUserId.isEmpty() && csvLocation.isNotEmpty()) {
                    val key = Normalize.text(csvLocation)
                    val matched = if (matchCache.containsKey(key)) {
                        matchCache[key]
                    } else {
                        LocationMatcher.match(csvLocation, usersByName, allUsers, nameWordsCache)
                            .also { matchCache[key] = it }
                    }
                    if (matched != null) {
                        boundUserId = matched
                        healed++
                    }
                }
                // привязали — значит текст приводим к ФИО из справочника,
                // иначе местоположение и ссылка разъедутся
                val location = usersById[boundUserId]?.fullName ?: r[7]
                EquipItem(id = r[0], category = r[1], brand = r[2], name = r[3], color = r[4],
                    weigh = r[5], quality = r[6], location = location, event = r[8], info = r[9], date = r[10],
                    locationUserId = boundUserId)
            }
            val dupId = items.groupingBy { it.id }.eachCount().entries.firstOrNull { it.value > 1 }?.key
            if (dupId != null) return ImportResponse(success = false, message = "Duplicate id in CSV: $dupId")

            equipItemRepository.deleteAll()
            equipItemRepository.saveAll(items)
            ImportResponse(
                success = true,
                importedCount = items.size,
                message = "восстановлено привязок: $restored, распознано по ФИО: $healed"
            )
        } catch (e: Exception) {
            ImportResponse(success = false, message = e.message)
        }
    }

    fun buildItemsXlsx(): ByteArray {
        val header = listOf("id", "category", "brand", "name", "color", "weigh", "quality", "location", "event", "info", "date")
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
        item.weigh, item.quality, item.location, item.event, item.info, item.date
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
