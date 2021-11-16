package me.zipi.navitotesla.db;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface PoiAddressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPoi(PoiAddressEntity poi);

    @Query("SELECT * FROM poi_address WHERE poi = :poi LIMIT 1")
    LiveData<PoiAddressEntity> findPoi(String poi);

    @Query("SELECT * FROM poi_address WHERE poi = :poi LIMIT 1")
    PoiAddressEntity findPoiSync(String poi);

    @Query("SELECT * FROM poi_address WHERE created < :date")
    List<PoiAddressEntity> findExpired(Long date);

    @Delete
    void delete(PoiAddressEntity poiAddressEntity);
}
