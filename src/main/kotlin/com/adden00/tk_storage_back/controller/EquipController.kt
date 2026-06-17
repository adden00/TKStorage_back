package com.adden00.tk_storage_back.controller

import com.adden00.tk_storage_back.dto.AddItemRequest
import com.adden00.tk_storage_back.dto.UpdateItemRequest
import com.adden00.tk_storage_back.service.EquipService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/items")
class EquipController(private val equipService: EquipService) {

    @GetMapping
    fun getAllItems() = equipService.getAllItems()

    @GetMapping("/{id}")
    fun getItem(@PathVariable id: String) = equipService.getItem(id)

    @PostMapping("/add")
    fun addItem(@RequestBody body: AddItemRequest) = equipService.addItem(body)

    @PutMapping("/update/{id}")
    fun updateItem(@PathVariable id: String, @RequestBody body: UpdateItemRequest) =
        equipService.updateItem(id, body)

    @GetMapping("/free-id")
    fun getFreeId() = equipService.getFreeId()

    @GetMapping("/search")
    fun search(@RequestParam query: String) = equipService.search(query)

    @GetMapping("/search/by-name")
    fun searchByName(@RequestParam query: String) = equipService.searchByName(query)

    @GetMapping("/{id}/history")
    fun getItemHistory(@PathVariable id: String) = equipService.getItemHistory(id)

    @GetMapping("/export/xls")
    fun exportXlsx(): ResponseEntity<ByteArray> {
        val bytes = equipService.buildItemsXlsx()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"equip_items.xlsx\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(bytes)
    }

    @GetMapping("/export/csv")
    fun exportCsv(): ResponseEntity<ByteArray> {
        val csv = equipService.buildItemsCsv()
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + csv.toByteArray(Charsets.UTF_8)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"equip_items.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(bytes)
    }
}
