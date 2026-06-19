package com.adden00.tk_storage_back.repository

import com.adden00.tk_storage_back.domain.EquipItem
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.util.regex.Pattern

interface EquipItemRepositorySearch {
    fun searchAcrossFields(words: List<String>): List<EquipItem>
}

private val SEARCH_FIELDS = listOf("location", "name", "brand", "category", "color", "event")

class EquipItemRepositorySearchImpl(private val mongoTemplate: MongoTemplate) : EquipItemRepositorySearch {

    override fun searchAcrossFields(words: List<String>): List<EquipItem> {
        if (words.isEmpty()) return emptyList()

        val wordCriteria = words.map { word ->
            val escaped = Pattern.quote(word)
            Criteria().orOperator(
                *SEARCH_FIELDS.map { field -> Criteria.where(field).regex(escaped, "i") }.toTypedArray()
            )
        }

        val combined = Criteria().andOperator(*wordCriteria.toTypedArray())
        return mongoTemplate.find(Query(combined), EquipItem::class.java)
    }
}
