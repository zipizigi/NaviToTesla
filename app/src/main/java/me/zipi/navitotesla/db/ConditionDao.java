package me.zipi.navitotesla.db;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface ConditionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCondition(ConditionEntity entity);

    @Query("SELECT * FROM condition WHERE type = :type")
    LiveData<List<ConditionEntity>> findCondition(String type);

    @Query("SELECT * FROM condition WHERE type = :type")
    List<ConditionEntity> findConditionSync(String type);

    @Query("SELECT * FROM condition WHERE type = :type AND name = :name")
    ConditionEntity findConditionByNameSync(String type, String name);

    @Delete
    void delete(ConditionEntity entity);

}
