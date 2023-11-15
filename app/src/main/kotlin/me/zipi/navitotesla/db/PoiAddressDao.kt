package me.zipi.navitotesla.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PoiAddressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPoi(poi: PoiAddressEntity)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updatePoi(poi: PoiAddressEntity)

    @Query("SELECT * FROM poi_address WHERE poi = :poi LIMIT 1")
    fun findPoi(poi: String): LiveData<PoiAddressEntity>

    @Query("SELECT * FROM poi_address WHERE poi = :poi LIMIT 1")
    fun findPoiSync(poi: String): PoiAddressEntity?

    @Query("SELECT * FROM poi_address WHERE created < :date AND (registered IS NULL OR registered = 0)")
    fun findExpired(date: Long): List<PoiAddressEntity>

    @Query("SELECT * FROM poi_address WHERE registered IS NULL OR registered = 0 ORDER BY created desc LIMIT :limit")
    fun findRecentPoiSync(limit: Int): List<PoiAddressEntity>

    @Query("SELECT * FROM poi_address WHERE registered = 1 ORDER BY poi")
    fun findRegisteredPoiSync(): List<PoiAddressEntity>

    @Delete
    fun delete(poiAddressEntity: PoiAddressEntity)

    @Query("DELETE FROM poi_address")
    @Deprecated("")
    fun deleteAll()

    @Query("DELETE FROM poi_address WHERE registered = 0")
    fun deleteAllNotRegistered()
}