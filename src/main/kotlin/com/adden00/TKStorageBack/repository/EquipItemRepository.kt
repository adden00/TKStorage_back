package com.adden00.TKStorageBack.repository

import com.adden00.TKStorageBack.domain.EquipItem
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query

interface EquipItemRepository : MongoRepository<EquipItem, String> {

    @Query("{ 'id': ?0 }")
    fun findByAppId(id: String): EquipItem?

    fun findByLocationContainingIgnoreCase(query: String): List<EquipItem>
}
