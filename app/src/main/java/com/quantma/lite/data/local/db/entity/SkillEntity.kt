package com.quantma.lite.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    /** Comma-separated keywords/regex patterns. */
    val triggerPatterns: String = "",
    /** Instructions injected into system prompt when triggered. */
    val promptInjection: String = "",
    @ColumnInfo(defaultValue = "1")
    val isEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val isBuiltIn: Boolean = false
)
