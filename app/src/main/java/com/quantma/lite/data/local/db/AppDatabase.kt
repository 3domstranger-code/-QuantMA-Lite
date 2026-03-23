package com.quantma.lite.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.quantma.lite.data.local.db.entity.AgentConfigEntity
import com.quantma.lite.data.local.db.entity.ChatMessageEntity
import com.quantma.lite.data.local.db.entity.ChatSessionEntity
import com.quantma.lite.data.local.db.entity.HookEntity
import com.quantma.lite.data.local.db.entity.RuleEntity
import com.quantma.lite.data.local.db.entity.SkillEntity

@Database(
    entities = [
        ChatMessageEntity::class,
        ChatSessionEntity::class,
        AgentConfigEntity::class,
        SkillEntity::class,
        RuleEntity::class,
        HookEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun agentConfigDao(): AgentConfigDao
    abstract fun skillDao(): SkillDao
    abstract fun ruleDao(): RuleDao
    abstract fun hookDao(): HookDao
}
