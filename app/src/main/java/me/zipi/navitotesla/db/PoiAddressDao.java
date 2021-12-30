package me.zipi.navitotesla.db;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface PoiAddressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPoi(PoiAddressEntity poi);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void updatePoi(PoiAddressEntity poi);

    @Query("SELECT * FROM poi_address WHERE poi = :poi LIMIT 1")
    LiveData<PoiAddressEntity> findPoi(String poi);

    @Query("SELECT * FROM poi_address WHERE poi = :poi LIMIT 1")
    PoiAddressEntity findPoiSync(String poi);

    @Query("SELECT * FROM poi_address WHERE created < :date AND (registered IS NULL OR registered = 0)")
    List<PoiAddressEntity> findExpired(Long date);

    @Query("SELECT * FROM poi_address WHERE registered IS NULL OR registered = 0 ORDER BY created desc LIMIT :limit")
    List<PoiAddressEntity> findRecentPoiSync(Integer limit);

    @Query("SELECT * FROM poi_address WHERE registered = 1 ORDER BY poi")
    List<PoiAddressEntity> findRegisteredPoiSync();

    @Delete
    void delete(PoiAddressEntity poiAddressEntity);

    @Query("DELETE FROM poi_address")
    @Deprecated
    void deleteAll();

    @Query("DELETE FROM poi_address WHERE registered = 0")
    void deleteAllNotRegistered();
}
