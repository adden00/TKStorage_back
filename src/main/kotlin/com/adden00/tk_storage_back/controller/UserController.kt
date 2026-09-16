package com.adden00.tk_storage_back.controller

import com.adden00.tk_storage_back.service.EquipService
import com.adden00.tk_storage_back.service.UserService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users")
class UserController(
    private val userService: UserService,
    private val equipService: EquipService
) {

    @GetMapping("/search")
    fun search(@RequestParam query: String) = userService.search(query)

    @GetMapping("/{id}")
    fun getUser(@PathVariable id: String) = userService.getUser(id)

    @GetMapping("/{id}/items")
    fun getUserItems(@PathVariable id: String) = equipService.getItemsOfUser(id)

    @PostMapping("/import/sheets")
    fun importFromSheets() = userService.importFromSheets()
}
