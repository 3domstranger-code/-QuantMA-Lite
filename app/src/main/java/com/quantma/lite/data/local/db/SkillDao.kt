package com.quantma.lite.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.quantma.lite.data.local.db.entity.SkillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillDao {

    @Query("SELECT * FROM skills ORDER BY isBuiltIn DESC, name ASC")
    fun observeAll(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE isEnabled = 1 ORDER BY name ASC")
    suspend fun getEnabled(): List<SkillEntity>

    @Query("SELECT COUNT(*) FROM skills")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(skill: SkillEntity): Long

    @Update
    suspend fun update(skill: SkillEntity)

    @Delete
    suspend fun delete(skill: SkillEntity)

    @Query("UPDATE skills SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
