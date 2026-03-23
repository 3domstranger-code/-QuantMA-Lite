package com.quantma.lite.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.quantma.lite.data.local.db.entity.AgentConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentConfigDao {

    @Query("SELECT * FROM agent_configs ORDER BY id ASC")
    fun getAllConfigs(): Flow<List<AgentConfigEntity>>

    /** Reactive — used by ChatViewModel to track active config changes. */
    @Query("SELECT * FROM agent_configs WHERE isDefault = 1 LIMIT 1")
    fun observeDefaultConfig(): Flow<AgentConfigEntity?>

    /** One-shot — used to check the default at a point in time. */
    @Query("SELECT * FROM agent_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultConfig(): AgentConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: AgentConfigEntity): Long

    @Update
    suspend fun update(config: AgentConfigEntity)

    @Delete
    suspend fun delete(config: AgentConfigEntity)

    @Query("UPDATE agent_configs SET isDefault = 0")
    suspend fun clearAllDefaults()

    @Query("UPDATE agent_configs SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)

    @Query("SELECT COUNT(*) FROM agent_configs")
    suspend fun getCount(): Int
}
