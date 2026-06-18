package com.adden00.tk_storage_back.repository

import com.adden00.tk_storage_back.domain.EquipItem
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query

private const val SEARCH_QUERY = "{ \"\$or\": [" +
    " { \"location\": { \"\$regex\": ?0, \"\$options\": \"i\" } }," +
    " { \"name\":     { \"\$regex\": ?0, \"\$options\": \"i\" } }," +
    " { \"brand\":    { \"\$regex\": ?0, \"\$options\": \"i\" } }," +
    " { \"category\": { \"\$regex\": ?0, \"\$options\": \"i\" } }," +
    " { \"color\":    { \"\$regex\": ?0, \"\$options\": \"i\" } }," +
    " { \"event\":    { \"\$regex\": ?0, \"\$options\": \"i\" } }" +
    " ] }"

data class MaxIdResult(val maxId: Long?)

interface EquipItemRepository : MongoRepository<EquipItem, String> {

    @Query("{ 'id': ?0 }")
    fun findByAppId(id: String): EquipItem?

    @Query(SEARCH_QUERY)
    fun searchAcrossFields(query: String): List<EquipItem>

    fun findByNameContainingIgnoreCase(query: String): List<EquipItem>

    @Aggregation(pipeline = ["{ '\$group': { '_id': null, 'maxId': { '\$max': { '\$toLong': '\$id' } } } }"])
    fun findMaxNumericId(): MaxIdResult?
}
