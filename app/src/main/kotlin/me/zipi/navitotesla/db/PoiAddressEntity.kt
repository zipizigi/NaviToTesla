package me.zipi.navitotesla.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.zipi.navitotesla.model.Poi
import java.util.Date
import kotlin.math.abs

@Entity(tableName = "poi_address", indices = [Index(value = ["poi"], unique = true)])
class PoiAddressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null,
    val poi: String,
    val address: String,
    val longitude: String? = null,
    val latitude: String? = null,
    val registered: Boolean? = null,
    val created: Date? = null,
) {
    val isExpire: Boolean
        get() {
            val createdTime = created?.time ?: return false
            val diff = createdTime - System.currentTimeMillis()
            return registered != null && !registered && abs(diff) / 1000L / 60L / 60L / 24L >= EXPIRE_DAY
        }

    fun isRegistered(): Boolean = registered != null && registered

    fun toPoi(): Poi = Poi(poiName = poi, roadAddress = address, address = address, longitude = longitude, latitude = latitude)

    companion object {
        const val EXPIRE_DAY = 10
    }
}
