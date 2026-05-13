package com.adden00.tk_storage_back.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {

    @GetMapping("/health")
    fun health() = mapOf("status" to "ok")
}
