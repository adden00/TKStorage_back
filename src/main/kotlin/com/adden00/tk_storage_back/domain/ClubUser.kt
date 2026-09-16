package com.adden00.tk_storage_back.domain

import com.adden00.tk_storage_back.dto.ClubUserDto
import com.adden00.tk_storage_back.dto.ClubUserShortDto
import com.adden00.tk_storage_back.util.Normalize
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "club_users")
data class ClubUser(
    @Id val mongoId: String? = null,
    @Indexed(unique = true) val id: String,
    val lastName: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val intake: String = "",
    val phone: String = "",
    val email: String = "",
    val telegram: String = "",
    val vk: String = "",
    val occupation: String = "",
    val birthDate: String = "",
    val address: String = "",
    val joinYear: String = "",
    val tourTraining: String = "",
    val rank: String = "",
    val rankExtra: String = "",
    val experience: String = "",
    val comment: String = "",
    val fullName: String = "",
    val birthDateNormalized: String = "",
    val searchText: String = "",
    val system: Boolean = false,
    val updatedAt: String = ""
) {
    companion object {
        const val ID_WAREHOUSE = "warehouse"
        const val ID_UNKNOWN = "unknown"

        /** Служебные места: дописываются к справочнику при каждой синхронизации. */
        val SYSTEM_USERS = listOf(
            ClubUser(
                id = ID_WAREHOUSE, lastName = "Склад", fullName = "Склад",
                searchText = "склад", system = true
            ),
            ClubUser(
                id = ID_UNKNOWN, lastName = "Неизвестно", fullName = "Неизвестно",
                searchText = "неизвестно", system = true
            )
        )

        /** Бизнес-ключ: ФИО + дата рождения. */
        fun buildId(lastName: String, firstName: String, middleName: String, birthDate: String): String =
            Normalize.sha256Short(
                listOf(
                    Normalize.text(lastName),
                    Normalize.text(firstName),
                    Normalize.text(middleName),
                    Normalize.birthDate(birthDate)
                ).joinToString("|")
            )

        fun buildFullName(lastName: String, firstName: String, middleName: String): String =
            listOf(lastName, firstName, middleName)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")

        /** Поиск идёт только по ФИО и телеграму. */
        fun buildSearchText(fullName: String, telegram: String): String =
            Normalize.text(fullName + " " + telegram.trim().removePrefix("@"))
    }
}

fun ClubUser.toShortDto() = ClubUserShortDto(
    id = id,
    fullName = fullName,
    birthDate = birthDateNormalized,
    telegram = telegram
)

fun ClubUser.toDto() = ClubUserDto(
    id = id,
    fullName = fullName,
    lastName = lastName,
    firstName = firstName,
    middleName = middleName,
    birthDate = birthDateNormalized,
    intake = intake,
    phone = phone,
    email = email,
    telegram = telegram,
    vk = vk,
    joinYear = joinYear,
    tourTraining = tourTraining,
    rank = rank,
    rankExtra = rankExtra,
    experience = experience
)
