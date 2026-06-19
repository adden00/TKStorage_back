package com.adden00.tk_storage_back.repository

import com.adden00.tk_storage_back.domain.EquipItem
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query

data class MaxIdResult(val maxId: Long?)

interface EquipItemRepository : MongoRepository<EquipItem, String>, EquipItemRepositorySearch {

    @Query("{ 'id': ?0 }")
    fun findByAppId(id: String): EquipItem?

    @Aggregation(pipeline = ["{ '\$group': { '_id': null, 'maxId': { '\$max': { '\$toLong': '\$id' } } } }"])
    fun findMaxNumericId(): MaxIdResult?
}
