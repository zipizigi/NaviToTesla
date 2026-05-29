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

    @Query("SELECT * FROM poi_address WHERE poi = :poi AND packageName = :packageName")
    suspend fun findPoiByPackage(
        poi: String,
        packageName: String,
    ): PoiAddressEntity?

    @Query("SELECT * FROM poi_address WHERE poi = :poi ORDER BY created DESC LIMIT 1")
    suspend fun findPoiLatest(poi: String): PoiAddressEntity?

    @Query("SELECT * FROM poi_address WHERE created < :date AND (registered IS NULL OR registered = 0)")
    suspend fun findExpired(date: Long): List<PoiAddressEntity>

    // 사용순 정렬: 마지막 share/classify 시점(lastCheckedAt) 기준 내림차순.
    // legacy row(lastCheckedAt 이 null) 는 IFNULL 로 created 시점 사용.
    @Query(
        "SELECT * FROM poi_address WHERE registered IS NULL OR registered = 0 " +
            "ORDER BY IFNULL(lastCheckedAt, created) DESC LIMIT :limit",
    )
    suspend fun findRecentPoi(limit: Int): List<PoiAddressEntity>

    @Query("SELECT * FROM poi_address WHERE registered = 1 ORDER BY poi")
    suspend fun findRegisteredPoi(): List<PoiAddressEntity>

    @Query("SELECT * FROM poi_address WHERE poi = :poi AND registered = 1 LIMIT 1")
    suspend fun findRegisteredByPoi(poi: String): PoiAddressEntity?

    @Delete
    suspend fun delete(poiAddressEntity: PoiAddressEntity)

    @Query("DELETE FROM poi_address")
    @Deprecated("")
    suspend fun deleteAll()

    @Query("DELETE FROM poi_address WHERE registered = 0")
    suspend fun deleteAllNotRegistered()
}
