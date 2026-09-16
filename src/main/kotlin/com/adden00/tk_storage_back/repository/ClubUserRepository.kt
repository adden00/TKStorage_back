package com.adden00.tk_storage_back.repository

import com.adden00.tk_storage_back.domain.ClubUser
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query

interface ClubUserRepository : MongoRepository<ClubUser, String>, ClubUserRepositorySearch {

    @Query("{ 'id': ?0 }")
    fun findByAppId(id: String): ClubUser?
}
