package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nickname: String,
    val host: String,
    val port: Int = 5252,
    val useHttps: Boolean = false,
    val password: String = "",
    val isDefault: Boolean = false,
    val autoRefreshSeconds: Int = 2,
    val createdAt: Long = System.currentTimeMillis()
) {
    val baseUrl: String
        get() {
            val scheme = if (useHttps) "https" else "http"
            return "$scheme://$host:$port"
        }
}
