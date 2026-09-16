package com.adden00.tk_storage_back.service

import com.adden00.tk_storage_back.domain.ClubUser
import com.adden00.tk_storage_back.domain.toDto
import com.adden00.tk_storage_back.domain.toShortDto
import com.adden00.tk_storage_back.dto.ImportResponse
import com.adden00.tk_storage_back.dto.UserResponse
import com.adden00.tk_storage_back.dto.UserSearchResponse
import com.adden00.tk_storage_back.repository.ClubUserRepository
import com.adden00.tk_storage_back.util.Csv
import com.adden00.tk_storage_back.util.Normalize
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Semaphore

@Service
class UserService(
    private val clubUserRepository: ClubUserRepository,
    private val equipService: EquipService,
    private val appScriptRestClient: RestClient,
    @Value("\${appscript.users.url}") private val usersUrl: String
) {

    companion object {
        private const val COLUMN_COUNT = 17
        private const val SEARCH_LIMIT = 50
        private const val MIN_QUERY_LENGTH = 2

        /**
         * Насколько справочник может усохнуть за один импорт. Apps Script под квотой
         * или таймаутом отдаёт не ошибку, а обрезанный CSV — и без этой проверки
         * пропавшие люди утащили бы за собой привязки, а ночная выгрузка тут же
         * записала бы потерю в таблицу.
         */
        private const val MIN_KEEP_RATIO = 0.5
    }

    // Ручной вызов может наложиться на плановый: два deleteAll внахлёст
    // оставили бы справочник наполовину пустым.
    private val importLock = Semaphore(1)

    fun importFromSheets(): ImportResponse {
        if (usersUrl.isBlank()) return ImportResponse(success = false, message = "Apps Script users URL not configured")
        if (!importLock.tryAcquire()) return ImportResponse(success = false, message = "Импорт уже выполняется")
        return try {
            // читаем байтами и декодируем сами: без charset в Content-Type
            // Spring взял бы ISO-8859-1 и молча испортил кириллицу
            val csv = appScriptRestClient.get()
                .uri(usersUrl)
                .retrieve()
                .body(ByteArray::class.java)
                ?.toString(Charsets.UTF_8)
                ?: return ImportResponse(success = false, message = "Пустой ответ от Apps Script")

            val rows = Csv.parse(csv).filter { row -> row.any { it.isNotBlank() } }
            if (rows.isEmpty()) return ImportResponse(success = false, message = "В выгрузке нет данных")

            var skipped = 0
            val parsed = mutableListOf<ClubUser>()
            for (row in rows) {
                // заголовка сейчас нет, но если появится — не хотим импортировать его как человека
                if (Normalize.text(row[0]) == "фамилия") continue
                if (row.size > COLUMN_COUNT) { skipped++; continue }
                val r = if (row.size < COLUMN_COUNT) row + List(COLUMN_COUNT - row.size) { "" } else row
                // без фамилии и имени запись бесполезна: ни ключа, ни поиска
                if (r[0].isBlank() && r[1].isBlank()) { skipped++; continue }
                parsed.add(toClubUser(r))
            }
            if (parsed.isEmpty()) return ImportResponse(success = false, message = "В выгрузке нет пригодных строк")

            // Дубли — это люди, заполнившие анкету дважды. Поздняя строка свежее,
            // но часто короче, поэтому сливаем по полям, а не берём её целиком.
            val merged = LinkedHashMap<String, ClubUser>()
            for (user in parsed) {
                val existing = merged[user.id]
                merged[user.id] = if (existing == null) user else merge(existing, user)
            }
            val deduped = merged.values.toList()

            val existing = (clubUserRepository.count() - ClubUser.SYSTEM_USERS.size).coerceAtLeast(0)
            if (existing > 0 && deduped.size < existing * MIN_KEEP_RATIO) {
                return ImportResponse(
                    success = false,
                    message = "В выгрузке ${deduped.size} записей против $existing в справочнике — " +
                        "похоже на обрезанный ответ. Справочник не тронут"
                )
            }

            clubUserRepository.deleteAll()
            clubUserRepository.saveAll(deduped + ClubUser.SYSTEM_USERS)

            // справочник изменился — привязки могли протухнуть. Новые совпадения
            // не ищем: привязку ставит только человек через приложение.
            val check = equipService.checkAgainstDirectory()

            ImportResponse(
                success = true,
                importedCount = deduped.size + ClubUser.SYSTEM_USERS.size,
                message = "пропущено строк: $skipped, схлопнуто дублей: ${parsed.size - deduped.size}, " +
                    "снято протухших привязок: ${check.cleared}, поправлен текст: ${check.renamed}"
            )
        } catch (e: Exception) {
            ImportResponse(success = false, message = e.message)
        } finally {
            importLock.release()
        }
    }

    fun search(query: String): UserSearchResponse {
        val normalized = Normalize.text(query)
        if (normalized.length < MIN_QUERY_LENGTH) {
            return UserSearchResponse(
                success = true,
                users = emptyList(),
                message = "Введите минимум $MIN_QUERY_LENGTH символа"
            )
        }
        val words = normalized.split(" ")
            .filter { it.isNotEmpty() }
            .map { it.removePrefix("@") }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return UserSearchResponse(success = true, users = emptyList())

        val found = clubUserRepository.searchByWords(words, SEARCH_LIMIT + 1)
        val hasMore = found.size > SEARCH_LIMIT
        return UserSearchResponse(
            success = true,
            users = found.take(SEARCH_LIMIT).map { it.toShortDto() },
            hasMore = hasMore,
            message = if (hasMore) "Показаны первые $SEARCH_LIMIT совпадений, уточните запрос" else null
        )
    }

    fun getUser(id: String): UserResponse {
        val user = clubUserRepository.findByAppId(id) ?: return UserResponse(success = false)
        return UserResponse(success = true, user = user.toDto())
    }

    private fun toClubUser(r: List<String>): ClubUser {
        val fullName = ClubUser.buildFullName(r[0], r[1], r[2])
        return ClubUser(
            id = ClubUser.buildId(r[0], r[1], r[2], r[9]),
            lastName = r[0].trim(),
            firstName = r[1].trim(),
            middleName = r[2].trim(),
            intake = r[3].trim(),
            phone = r[4].trim(),
            email = r[5].trim(),
            telegram = r[6].trim(),
            vk = r[7].trim(),
            occupation = r[8].trim(),
            birthDate = r[9].trim(),
            address = r[10].trim(),
            joinYear = r[11].trim(),
            tourTraining = r[12].trim(),
            rank = r[13].trim(),
            rankExtra = r[14].trim(),
            experience = r[15].trim(),
            comment = r[16].trim(),
            fullName = fullName,
            birthDateNormalized = Normalize.birthDate(r[9]),
            searchText = ClubUser.buildSearchText(fullName, r[6]),
            updatedAt = currentTime()
        )
    }

    /** Значение из поздней анкеты, а где оно пустое — из ранней. */
    private fun merge(old: ClubUser, new: ClubUser) = new.copy(
        intake = new.intake.ifBlank { old.intake },
        phone = new.phone.ifBlank { old.phone },
        email = new.email.ifBlank { old.email },
        telegram = new.telegram.ifBlank { old.telegram },
        vk = new.vk.ifBlank { old.vk },
        occupation = new.occupation.ifBlank { old.occupation },
        address = new.address.ifBlank { old.address },
        joinYear = new.joinYear.ifBlank { old.joinYear },
        tourTraining = new.tourTraining.ifBlank { old.tourTraining },
        rank = new.rank.ifBlank { old.rank },
        rankExtra = new.rankExtra.ifBlank { old.rankExtra },
        experience = new.experience.ifBlank { old.experience },
        comment = new.comment.ifBlank { old.comment }
    ).let { merged ->
        merged.copy(searchText = ClubUser.buildSearchText(merged.fullName, merged.telegram))
    }

    private fun currentTime(): String =
        ZonedDateTime.now(ZoneId.of("GMT+3"))
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
}
