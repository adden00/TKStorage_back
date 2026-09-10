package com.adden00.tk_storage_back.service

import com.adden00.tk_storage_back.domain.ClubUser
import com.adden00.tk_storage_back.util.Normalize

/**
 * Сопоставляет текст местоположения из гугл-таблицы с записью справочника.
 *
 * В таблице людей пишут как придётся: одной фамилией ("Патракеев"), в обратном
 * порядке ("Аня Глущенко"), уменьшительным именем ("Тереничев Дима"). Привязка
 * ставится только при однозначном совпадении — иначе вещь молча уйдёт не тому
 * человеку, а это хуже, чем оставить свободный текст.
 */
object LocationMatcher {

    /** Слова короче этого в расчёт не берём: "ЗЗ" находит "Ляззат". */
    private const val MIN_WORD_LENGTH = 3

    /** Пояснения в скобках — комментарий кладовщика, а не часть имени. */
    private val NOTE_IN_BRACKETS = Regex("\\([^)]*\\)")
    private val UNCLOSED_NOTE = Regex("\\(.*$")

    /** Тексты, у которых нет однозначного совпадения и которые развели вручную. */
    private val ALIAS_TO_ID: Map<String, String> = mapOf(
        Normalize.text("новый склад") to ClubUser.ID_UNKNOWN,
        Normalize.text("ЗЗ") to ClubUser.ID_UNKNOWN,
        // однофамильцев слишком много, а "Денисович" ловит ещё и Смирнова Артёма
        Normalize.text("Денисов") to ClubUser.buildId("Денисов", "Артём", "Денисович", "19.08.2000"),
        // Кузнецовых Сергеев несколько, нужного опознали по телеграму @yneirr
        Normalize.text("кузнецов Сергей") to
            ClubUser.buildId("Кузнецов", "Сергей", "Владимирович", "21.08.2002")
    )

    /** Тексты, которым не хватает слова, чтобы стать однозначными. */
    private val ALIAS_TO_TEXT: Map<String, String> = mapOf(
        Normalize.text("Алёшин") to Normalize.text("Алёшин Тимофей"),
        Normalize.text("Тимофей") to Normalize.text("Алёшин Тимофей"),
        Normalize.text("Тимур") to Normalize.text("Шафиев Тимур"),
        // "Слава" подходит и Вячеславу, и Владиславу — здесь имелся в виду Вячеслав
        Normalize.text("Алексюк") to Normalize.text("Алексюк Вячеслав"),
        Normalize.text("Алексюк Слава") to Normalize.text("Алексюк Вячеслав"),
        // опечатка в таблице: правильная фамилия — Некеров
        Normalize.text("Ярослав Некров") to Normalize.text("Некеров Ярослав")
    )

    /**
     * Уменьшительные и обиходные формы имён. Значение — список, потому что одна
     * форма может раскрываться в несколько имён; если из-за этого кандидатов
     * окажется больше одного, привязка не ставится.
     */
    private val DIMINUTIVES: Map<String, List<String>> = mapOf(
        "дима" to listOf("дмитрий"),
        "аня" to listOf("анна"),
        "настя" to listOf("анастасия"),
        "оля" to listOf("ольга"),
        "ксеня" to listOf("ксения"),
        "сережа" to listOf("сергей"),
        "серега" to listOf("сергей"),
        "слава" to listOf("вячеслав", "владислав", "святослав", "ярослав"),
        "саша" to listOf("александр"),
        "шура" to listOf("александр"),
        "женя" to listOf("евгений", "евгения"),
        "миша" to listOf("михаил"),
        "маша" to listOf("мария"),
        "лена" to listOf("елена"),
        "катя" to listOf("екатерина"),
        "паша" to listOf("павел"),
        "коля" to listOf("николай"),
        "вова" to listOf("владимир"),
        "володя" to listOf("владимир"),
        "таня" to listOf("татьяна"),
        "света" to listOf("светлана"),
        "ира" to listOf("ирина"),
        "юля" to listOf("юлия"),
        "леша" to listOf("алексей"),
        "леха" to listOf("алексей"),
        "витя" to listOf("виктор"),
        "боря" to listOf("борис"),
        "гриша" to listOf("григорий"),
        "костя" to listOf("константин"),
        "надя" to listOf("надежда"),
        "даша" to listOf("дарья"),
        "толя" to listOf("анатолий"),
        "вася" to listOf("василий"),
        "петя" to listOf("пётр"),
        "федя" to listOf("фёдор"),
        "степа" to listOf("степан"),
        "соня" to listOf("софья", "софия"),
        "лиза" to listOf("елизавета"),
        "рита" to listOf("маргарита"),
        "тима" to listOf("тимофей", "тимур"),
        "тоша" to listOf("антон"),
        "гоша" to listOf("георгий", "игорь"),
        "юра" to listOf("юрий"),
        "ваня" to listOf("иван"),
        "макс" to listOf("максим"),
        "ник" to listOf("никита", "николай"),
        "зина" to listOf("зинаида"),
        "люба" to listOf("любовь"),
        "люда" to listOf("людмила"),
        "галя" to listOf("галина"),
        "валя" to listOf("валентина", "валентин"),
        "тася" to listOf("таисия"),
        "лера" to listOf("валерия"),
        "поля" to listOf("полина"),
        "варя" to listOf("варвара"),
        "стас" to listOf("станислав"),
        "андрюха" to listOf("андрей"),
        "серж" to listOf("сергей")
    )

    /**
     * Убирает пояснения в скобках: "Сагирова Арина (брал Тереничев и передал ей)"
     * должно искаться как "Сагирова Арина", иначе лишние слова не дадут совпасть.
     * Незакрытая скобка отрезается до конца строки.
     */
    fun stripNotes(text: String): String =
        Normalize.text(
            text.replace(NOTE_IN_BRACKETS, " ").replace(UNCLOSED_NOTE, " ")
        )

    /** Разбирает текст на значимые слова; пустой список означает "искать не по чему". */
    private fun words(text: String): List<String> =
        Normalize.text(text)
            .split(" ")
            .filter { it.length >= MIN_WORD_LENGTH }

    /**
     * Слово подходит, если оно само или его полная форма точно равна слову из ФИО.
     *
     * Совпадение по началу слова пробовали: на реальных данных оно не дало ни одной
     * верной привязки сверх точного сравнения, зато привязало вещи "Хайзников"
     * к "Хайзниковой" — женская форма фамилии начинается так же, как мужская.
     */
    private fun wordMatches(word: String, nameWords: List<String>): Boolean {
        val variants = listOf(word) + (DIMINUTIVES[word] ?: emptyList())
        return nameWords.any { nameWord -> variants.any { nameWord == it } }
    }

    /**
     * Возвращает id записи справочника либо null, если совпадение не единственное.
     * [usersByName] — точные совпадения по нормализованному ФИО, [allUsers] — все записи.
     */
    fun match(
        rawLocation: String,
        usersByName: Map<String, List<ClubUser>>,
        allUsers: List<ClubUser>,
        nameWordsCache: Map<String, List<String>>
    ): String? {
        val normalized = stripNotes(rawLocation)
        if (normalized.isEmpty()) return null

        ALIAS_TO_ID[normalized]?.let { return it }
        val effective = ALIAS_TO_TEXT[normalized] ?: normalized

        // точное совпадение ФИО — самый надёжный случай
        usersByName[effective]?.singleOrNull()?.let { return it.id }

        val queryWords = words(effective)
        if (queryWords.isEmpty()) return null

        var found: ClubUser? = null
        for (user in allUsers) {
            val nameWords = nameWordsCache[user.id] ?: continue
            if (queryWords.all { wordMatches(it, nameWords) }) {
                if (found != null) return null   // неоднозначно — оставляем свободный текст
                found = user
            }
        }
        return found?.id
    }

    fun nameWords(user: ClubUser): List<String> =
        Normalize.text(user.fullName).split(" ").filter { it.isNotEmpty() }
}
