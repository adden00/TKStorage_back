package com.adden00.tk_storage_back.controller

import com.adden00.tk_storage_back.service.EquipService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/history")
class HistoryController(private val equipService: EquipService) {

    @GetMapping
    fun getHistory(
        @RequestParam(required = false) before: String?,
        @RequestParam(defaultValue = "30") size: Int
    ) = equipService.getHistoryPage(before, size)
}
