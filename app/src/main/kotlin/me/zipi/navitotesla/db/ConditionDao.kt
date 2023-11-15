package me.zipi.navitotesla.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConditionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCondition(entity: ConditionEntity)

    @Query("SELECT * FROM condition WHERE type = :type")
    fun findCondition(type: String?): LiveData<List<ConditionEntity>>

    @Query("SELECT * FROM condition WHERE type = :type")
    fun findConditionSync(type: String): List<ConditionEntity>

    @Query("SELECT * FROM condition WHERE type = :type AND name = :name")
    fun findConditionByNameSync(type: String, name: String): ConditionEntity?

    @Delete
    fun delete(entity: ConditionEntity)
}