package com.quantma.lite.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hooks")
data class HookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** PRE_COMMIT, POST_COMMIT, ON_FILE_CHANGE, ON_ERROR */
    val trigger: String,
    val action: String,
    @ColumnInfo(defaultValue = "1")
    val isEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val isBuiltIn: Boolean = false
)
