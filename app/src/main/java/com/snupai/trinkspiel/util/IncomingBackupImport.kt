package com.snupai.trinkspiel.util

data class IncomingBackupImport(
    val id: Long,
    val sourceLabel: String,
    val json: String,
)
