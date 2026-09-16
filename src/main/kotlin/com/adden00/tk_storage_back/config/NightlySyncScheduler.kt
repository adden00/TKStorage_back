package com.adden00.tk_storage_back.config

import com.adden00.tk_storage_back.service.EquipService
import com.adden00.tk_storage_back.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

/**
 * Ночная синхронизация: сначала справочник людей из гугл-таблицы, следом выгрузка
 * снаряжения обратно в таблицу. Два шага в одном событии и строго в этом порядке —
 * импорт справочника может снять протухшие привязки и поправить текст, и эти
 * изменения тоже должны уехать в таблицу.
 *
 * Выгрузка нужна потому, что привязки живут только в Mongo, пока их не выгрузили:
 * id попадают в таблицу и лишь оттуда переживают следующий импорт снаряжения.
 */
@Configuration
@EnableScheduling
class NightlySyncScheduler(
    private val userService: UserService,
    private val equipService: EquipService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // Значение "-" отключает задачу, поэтому без свойства синхронизация не запустится.
    @Scheduled(cron = "\${appscript.sync.cron:-}", zone = "Europe/Moscow")
    fun sync() {
        val users = userService.importFromSheets()
        if (users.success) {
            log.info("Ночная синхронизация, справочник: count={}, {}", users.importedCount, users.message)
        } else {
            log.warn("Ночная синхронизация, справочник не обновлён: {}", users.message)
        }

        // Выгрузку делаем в любом случае: если справочник не скачался, снаряжение от этого
        // не изменилось, а дневные правки из приложения всё равно надо дослать в таблицу.
        val export = equipService.exportToSheets()
        if (export.success) {
            log.info("Ночная синхронизация, выгрузка снаряжения: успешно")
        } else {
            log.warn("Ночная синхронизация, выгрузка не удалась: {}", export.message)
        }
    }
}
