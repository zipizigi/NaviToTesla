package me.zipi.navitotesla.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DestinationSendCacheDao {
    @Query(
        "SELECT * FROM destination_send_cache " +
            "WHERE poi = :poi AND packageName = :packageName LIMIT 1",
    )
    suspend fun find(
        poi: String,
        packageName: String,
    ): DestinationSendCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DestinationSendCacheEntity)

    @Query("DELETE FROM destination_send_cache WHERE created < :expireBefore")
    suspend fun deleteExpired(expireBefore: Long)
}
