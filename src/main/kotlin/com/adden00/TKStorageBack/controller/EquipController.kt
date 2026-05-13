package com.adden00.TKStorageBack.controller

import com.adden00.TKStorageBack.dto.*
import com.adden00.TKStorageBack.service.EquipService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val MIN_VERSION_CODE = 2
private val VERSION_ERROR = ErrorResponse(message = "Пожалуйста обновите приложение!")

@RestController
class EquipController(private val equipService: EquipService) {

    @PostMapping("/", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun handle(
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) versionCode: String?,
        @RequestParam(required = false) id: String?,
        @RequestParam(required = false) itemId: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) brand: String?,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) color: String?,
        @RequestParam(required = false) weigh: String?,
        @RequestParam(required = false) quality: String?,
        @RequestParam(required = false) location: String?,
        @RequestParam(required = false) event: String?,
        @RequestParam(required = false) info: String?,
        @RequestParam(required = false) date: String?,
        @RequestParam(required = false) keyholderName: String?,
        @RequestParam(required = false) query: String?
    ): Any {
        if (versionCode == null || (versionCode.toIntOrNull() ?: 0) < MIN_VERSION_CODE) {
            return VERSION_ERROR
        }

        val params = ItemRequestParams(
            type, versionCode, id, itemId, category, brand,
            name, color, weigh, quality, location, event, info, date, keyholderName, query
        )

        return when (type) {
            "get"       -> equipService.getItem(id ?: return ItemResponse(success = false))
            "update"    -> equipService.updateItem(params)
            "add"       -> equipService.addItem(params)
            "getFreeId" -> equipService.getFreeId()
            "search"    -> equipService.search(query ?: "")
            else        -> ErrorResponse(message = "Unknown type")
        }
    }
}
