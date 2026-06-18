package com.adden00.tk_storage_back.service

import com.adden00.tk_storage_back.domain.EquipItem
import com.adden00.tk_storage_back.domain.HistoryEntry
import com.adden00.tk_storage_back.domain.toItem
import com.adden00.tk_storage_back.dto.*
import com.adden00.tk_storage_back.repository.EquipItemRepository
import com.adden00.tk_storage_back.repository.HistoryEntryRepository
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Service
class EquipService(
    private val equipItemRepository: EquipItemRepository,
    private val historyEntryRepository: HistoryEntryRepository,
    private val appScriptRestClient: RestClient,
    @Value("\${appscript.export.url}") private val appScriptUrl: String,
    @Value("\${appscript.import.url}") private val appScriptImportUrl: String
) {

    companion object {
        private const val HEADER = "id,category,brand,name,color,weigh,quality,location,event,info,date"
        private const val COLUMN_COUNT = 11
    }

    fun getItem(id: String): ItemResponse {
        val item = equipItemRepository.findByAppId(id) ?: return ItemResponse(success = false)
        return ItemResponse(success = true, equipItem = buildDto(item))
    }

    fun updateItem(id: String, body: UpdateItemRequest): ItemResponse {
        val item = equipItemRepository.findByAppId(id) ?: return ItemResponse(success = false)

        historyEntryRepository.save(
            HistoryEntry(
                action = body.historyAction,
                id = sumIfDifferent(item.id, body.newItem.id),
                category = sumIfDifferent(item.category, body.newItem.category),
                brand = sumIfDifferent(item.brand, body.newItem.brand),
                name = sumIfDifferent(item.name, body.newItem.name),
                color = sumIfDifferent(item.color, body.newItem.color),
                weigh = sumIfDifferent(item.weigh, body.newItem.weigh),
                quality = sumIfDifferent(item.quality, body.newItem.quality),
                location = sumIfDifferent(item.location, body.newItem.location),
                event = sumIfDifferent(item.event, body.newItem.event),
                info = sumIfDifferent(item.info, body.newItem.info),
                timestamp = getCurrentTime(),
                keyholderName = body.keyholderName
            )
        )

        val updated = item.copy(
            id = body.newItem.id,
            category = body.newItem.category,
            brand = body.newItem.brand,
            name = body.newItem.name,
            color = body.newItem.color,
            weigh = body.newItem.weigh,
            quality = body.newItem.quality,
            location = body.newItem.location,
            event = body.newItem.event,
            info = body.newItem.info,
            date = body.newItem.date
        )
        equipItemRepository.save(updated)
        return ItemResponse(success = true, equipItem = buildDto(updated))
    }

    fun addItem(body: AddItemRequest): ItemResponse {
        return try {
            equipItemRepository.save(body.newItem.toItem())
            historyEntryRepository.save(
                HistoryEntry(
                    action = "ДОБАВЛЕНО",
                    id = body.newItem.id,
                    category = body.newItem.category,
                    brand = body.newItem.brand,
                    name = body.newItem.name,
                    color = body.newItem.color,
                    weigh = body.newItem.weigh,
                    quality = body.newItem.quality,
                    location = body.newItem.location,
                    event = body.newItem.event,
                    info = body.newItem.info,
                    timestamp = getCurrentTime(),
                    keyholderName = body.keyholderName
                )
            )
            ItemResponse(success = true, equipItem = body.newItem)
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

    fun search(query: String): SearchResponse {
        val items = equipItemRepository
            .searchAcrossFields(Pattern.quote(query))
            .map { buildDto(it) }
        return SearchResponse(success = true, items = items)
    }

    fun searchByName(query: String): SearchResponse {
        val items = equipItemRepository.findByNameContainingIgnoreCase(query).map { buildDto(it) }
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
            itemValues(item).joinToString(",") { csvEscape(it) }
        }
        return (if (includeHeader) listOf(HEADER) + rows else rows).joinToString("\r\n")
    }

    fun importFromSheets(): ImportResponse {
        if (appScriptImportUrl.isBlank()) return ImportResponse(success = false, message = "Apps Script import URL not configured")
        return try {
            val csv = appScriptRestClient.get()
                .uri(appScriptImportUrl)
                .retrieve()
                .body(String::class.java)
                ?: return ImportResponse(success = false, message = "Empty response from Apps Script")

            val rows = parseCsv(csv).filter { it.isNotEmpty() && it.any { c -> c.isNotBlank() } }
            if (rows.isEmpty()) return ImportResponse(success = false, message = "CSV has no data rows")
            if (rows.any { it.size != COLUMN_COUNT })
                return ImportResponse(success = false, message = "Malformed CSV: wrong column count")

            val items = rows.map { r ->
                EquipItem(id = r[0], category = r[1], brand = r[2], name = r[3], color = r[4],
                    weigh = r[5], quality = r[6], location = r[7], event = r[8], info = r[9], date = r[10])
            }
            val dupId = items.groupingBy { it.id }.eachCount().entries.firstOrNull { it.value > 1 }?.key
            if (dupId != null) return ImportResponse(success = false, message = "Duplicate id in CSV: $dupId")

            equipItemRepository.deleteAll()
            equipItemRepository.saveAll(items)
            ImportResponse(success = true, importedCount = items.size)
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

    // RFC4180-совместимый парсер: корректно обрабатывает поля в кавычках,
    // включая запятые и переносы строк внутри полей, а также удвоенные кавычки.
    private fun parseCsv(text: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        // снять BOM если есть
        val input = text.trimStart('﻿')
        var i = 0
        var inQuotes = false
        while (i < input.length) {
            val ch = input[i]
            when {
                inQuotes && ch == '"' && i + 1 < input.length && input[i + 1] == '"' -> {
                    field.append('"'); i += 2
                }
                inQuotes && ch == '"' -> { inQuotes = false; i++ }
                !inQuotes && ch == '"' -> { inQuotes = true; i++ }
                !inQuotes && ch == ',' -> { row.add(field.toString()); field.clear(); i++ }
                !inQuotes && ch == '\r' && i + 1 < input.length && input[i + 1] == '\n' -> {
                    row.add(field.toString()); field.clear()
                    result.add(row.toList()); row.clear(); i += 2
                }
                !inQuotes && (ch == '\n' || ch == '\r') -> {
                    row.add(field.toString()); field.clear()
                    result.add(row.toList()); row.clear(); i++
                }
                else -> { field.append(ch); i++ }
            }
        }
        // последняя строка без финального переноса
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            result.add(row.toList())
        }
        return result
    }

    private fun csvEscape(value: String): String {
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
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
        if (old == new) old else "$old-->$new"

    private fun getCurrentTime(): String =
        ZonedDateTime.now(ZoneId.of("GMT+3"))
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
}
