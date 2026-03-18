package me.zipi.navitotesla.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.zipi.navitotesla.model.Poi
import java.util.Date
import kotlin.math.abs

@Entity(tableName = "poi_address", indices = [Index(value = ["poi", "packageName"], unique = true)])
class PoiAddressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null,
    val poi: String,
    val address: String,
    val longitude: String? = null,
    val latitude: String? = null,
    val registered: Boolean? = null,
    val created: Date? = null,
    val packageName: String? = null,
    val isDuplicate: Boolean? = null,
) {
    val isExpire: Boolean
        get() {
            val createdTime = created?.time ?: return false
            if (registered != null && registered) return false
            val ageInDays = abs(System.currentTimeMillis() - createdTime) / 1000L / 60L / 60L / 24L
            val expireDays = if (isDuplicate == true) DUPLICATE_EXPIRE_DAY else EXPIRE_DAY
            return ageInDays >= expireDays
        }

    fun isRegistered(): Boolean = registered != null && registered

    fun toPoi(): Poi =
        Poi(
            poiName = poi,
            roadAddress = address,
            address = address,
            longitude = longitude,
            latitude = latitude,
            packageName = packageName ?: "",
            isDuplicate = isDuplicate == true,
        )

    companion object {
        const val EXPIRE_DAY = 10
        const val DUPLICATE_EXPIRE_DAY = 1
    }
}
