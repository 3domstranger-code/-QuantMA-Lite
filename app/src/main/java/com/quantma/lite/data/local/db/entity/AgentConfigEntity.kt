package com.quantma.lite.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_configs")
data class AgentConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val systemPrompt: String,
    val role: String = "ASSISTANT",
    val customInstructions: String = "",
    val restrictions: String = "",
    @ColumnInfo(defaultValue = "0")
    val isDefault: Boolean = false
)
