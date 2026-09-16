package com.adden00.tk_storage_back.repository

import com.adden00.tk_storage_back.domain.ClubUser
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.util.regex.Pattern

interface ClubUserRepositorySearch {
    fun searchByWords(words: List<String>, limit: Int): List<ClubUser>
}

class ClubUserRepositorySearchImpl(private val mongoTemplate: MongoTemplate) : ClubUserRepositorySearch {

    // Поиск идёт по одному предвычисленному полю: нормализация на входе решает то,
    // чего regex по сырым значениям не может — ё/е и телеграм без собаки.
    override fun searchByWords(words: List<String>, limit: Int): List<ClubUser> {
        if (words.isEmpty()) return emptyList()
        val criteria = Criteria().andOperator(
            *words.map { Criteria.where("searchText").regex(Pattern.quote(it), "i") }.toTypedArray()
        )
        // служебные записи ("Склад", "Неизвестно") показываем первыми
        val sort = Sort.by(Sort.Direction.DESC, "system").and(Sort.by("fullName"))
        return mongoTemplate.find(Query(criteria).limit(limit).with(sort), ClubUser::class.java)
    }
}
