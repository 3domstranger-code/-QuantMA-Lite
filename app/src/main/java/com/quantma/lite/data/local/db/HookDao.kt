package com.quantma.lite.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.quantma.lite.data.local.db.entity.HookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HookDao {

    @Query("SELECT * FROM hooks ORDER BY isBuiltIn DESC, trigger ASC, id ASC")
    fun observeAll(): Flow<List<HookEntity>>

    @Query("SELECT * FROM hooks WHERE isEnabled = 1 AND trigger = :trigger")
    suspend fun getEnabledByTrigger(trigger: String): List<HookEntity>

    @Query("SELECT COUNT(*) FROM hooks")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(hook: HookEntity): Long

    @Update
    suspend fun update(hook: HookEntity)

    @Delete
    suspend fun delete(hook: HookEntity)

    @Query("UPDATE hooks SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
