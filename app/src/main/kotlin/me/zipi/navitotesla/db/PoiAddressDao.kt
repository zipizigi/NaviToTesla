package me.zipi.navitotesla.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PoiAddressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoi(poi: PoiAddressEntity)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updatePoi(poi: PoiAddressEntity)

    @Query("SELECT * FROM poi_address WHERE poi = :poi LIMIT 1")
    suspend fun findPoi(poi: String): PoiAddressEntity?

    @Query("SELECT * FROM poi_address WHERE created < :date AND (registered IS NULL OR registered = 0)")
    suspend fun findExpired(date: Long): List<PoiAddressEntity>

    @Query("SELECT * FROM poi_address WHERE registered IS NULL OR registered = 0 ORDER BY created desc LIMIT :limit")
    suspend fun findRecentPoi(limit: Int): List<PoiAddressEntity>

    @Query("SELECT * FROM poi_address WHERE registered = 1 ORDER BY poi")
    suspend fun findRegisteredPoi(): List<PoiAddressEntity>

    @Delete
    suspend fun delete(poiAddressEntity: PoiAddressEntity)

    @Query("DELETE FROM poi_address")
    @Deprecated("")
    suspend fun deleteAll()

    @Query("DELETE FROM poi_address WHERE registered = 0")
    suspend fun deleteAllNotRegistered()
}