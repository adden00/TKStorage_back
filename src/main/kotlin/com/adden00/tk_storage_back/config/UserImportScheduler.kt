package com.adden00.tk_storage_back.config

import com.adden00.tk_storage_back.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

@Configuration
@EnableScheduling
class UserImportScheduler(private val userService: UserService) {

    private val log = LoggerFactory.getLogger(javaClass)

    // Значение "-" отключает задачу, поэтому без свойства планировщик просто не стартует.
    @Scheduled(cron = "\${appscript.users.import.cron:-}", zone = "Europe/Moscow")
    fun importUsers() {
        val result = userService.importFromSheets()
        log.info(
            "Плановый импорт пользователей: success={}, count={}, {}",
            result.success, result.importedCount, result.message
        )
    }
}
