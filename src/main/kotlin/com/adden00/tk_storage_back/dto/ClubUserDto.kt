package com.adden00.tk_storage_back.dto

/** Выдача поиска: ФИО + дата рождения + телеграм — этого хватает, чтобы различить тёзок. */
data class ClubUserShortDto(
    val id: String,
    val fullName: String = "",
    val birthDate: String = "",
    val telegram: String = ""
)

/**
 * Полный профиль. Адрес, место работы и комментарий наружу не отдаются —
 * складскому приложению они не нужны ни в одном сценарии.
 */
data class ClubUserDto(
    val id: String,
    val fullName: String = "",
    val lastName: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val birthDate: String = "",
    val intake: String = "",
    val phone: String = "",
    val email: String = "",
    val telegram: String = "",
    val vk: String = "",
    val joinYear: String = "",
    val tourTraining: String = "",
    val rank: String = "",
    val rankExtra: String = "",
    val experience: String = ""
)
