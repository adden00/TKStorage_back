package com.adden00.tk_storage_back.util

import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

object Normalize {

    private val OUT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /**
     * Настоящая дата в ячейке таблицы приезжает в ISO — иногда со временем.
     * Это тот же день, что и "24.01.2004" текстом, и разойтись в ключе они не должны.
     */
    private val ISO_DATE = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[t ].*)?")

    private val BIRTH_PATTERNS = listOf(
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("d.M.yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        // двузначный год: база 1930, иначе "25.07.97" превратится в 2097
        DateTimeFormatterBuilder()
            .appendPattern("d.M.")
            .appendValueReduced(ChronoField.YEAR, 2, 2, 1930)
            .toFormatter(),
        DateTimeFormatterBuilder()
            .appendPattern("d-M-")
            .appendValueReduced(ChronoField.YEAR, 2, 2, 1930)
            .toFormatter()
    )

    /** Приводит текст к виду, пригодному для сравнения и поиска. */
    fun text(s: String): String =
        s.trim().lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ")

    fun digits(s: String): String = s.filter { it.isDigit() }

    /**
     * Дата рождения в виде dd.MM.yyyy там, где её удалось разобрать.
     * Всё остальное возвращается как нормализованный текст — главное, чтобы
     * результат был детерминированным: он участвует в бизнес-ключе пользователя.
     */
    fun birthDate(raw: String): String {
        val compact = text(raw).replace(" ", "")
        if (compact.isEmpty() || compact == "-" || compact == "—") return ""
        ISO_DATE.matchEntire(text(raw))?.destructured?.let { (year, month, day) ->
            runCatching { return LocalDate.of(year.toInt(), month.toInt(), day.toInt()).format(OUT) }
        }
        for (pattern in BIRTH_PATTERNS) {
            runCatching { return LocalDate.parse(compact, pattern).format(OUT) }
        }
        return text(raw)
    }

    fun sha256Short(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)
}
