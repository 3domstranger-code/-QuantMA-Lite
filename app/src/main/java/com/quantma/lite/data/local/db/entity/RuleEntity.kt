package com.quantma.lite.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** CODE_STYLE, ARCHITECTURE, SECURITY, COMMENTS */
    val category: String,
    val instruction: String,
    @ColumnInfo(defaultValue = "1")
    val isEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val isBuiltIn: Boolean = false
)
