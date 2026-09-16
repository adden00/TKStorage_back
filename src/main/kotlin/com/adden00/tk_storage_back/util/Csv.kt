package com.adden00.tk_storage_back.util

object Csv {

    // RFC4180-совместимый парсер: корректно обрабатывает поля в кавычках,
    // включая запятые и переносы строк внутри полей, а также удвоенные кавычки.
    fun parse(text: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        // снять BOM если есть
        val input = text.trimStart('﻿')
        var i = 0
        var inQuotes = false
        while (i < input.length) {
            val ch = input[i]
            when {
                inQuotes && ch == '"' && i + 1 < input.length && input[i + 1] == '"' -> {
                    field.append('"'); i += 2
                }
                inQuotes && ch == '"' -> { inQuotes = false; i++ }
                !inQuotes && ch == '"' -> { inQuotes = true; i++ }
                !inQuotes && ch == ',' -> { row.add(field.toString()); field.clear(); i++ }
                !inQuotes && ch == '\r' && i + 1 < input.length && input[i + 1] == '\n' -> {
                    row.add(field.toString()); field.clear()
                    result.add(row.toList()); row.clear(); i += 2
                }
                !inQuotes && (ch == '\n' || ch == '\r') -> {
                    row.add(field.toString()); field.clear()
                    result.add(row.toList()); row.clear(); i++
                }
                else -> { field.append(ch); i++ }
            }
        }
        // последняя строка без финального переноса
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            result.add(row.toList())
        }
        return result
    }

    fun escape(value: String): String {
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
