package me.zipi.navitotesla.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConditionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCondition(entity: ConditionEntity)


    @Query("SELECT * FROM condition WHERE type = :type")
    suspend fun findCondition(type: String): List<ConditionEntity>

    @Query("SELECT * FROM condition WHERE type = :type AND name = :name")
    suspend fun findConditionByName(type: String, name: String): ConditionEntity?

    @Delete
    suspend fun delete(entity: ConditionEntity)
}