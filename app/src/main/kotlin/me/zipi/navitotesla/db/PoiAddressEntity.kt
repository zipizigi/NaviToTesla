package me.zipi.navitotesla.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Calendar
import java.util.Date
import kotlin.math.abs

@Entity(tableName = "poi_address", indices = [Index(value = ["poi"], unique = true)])
class PoiAddressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null,
    val poi: String,
    val address: String,
    val registered: Boolean? = null,
    val created: Date? = null,
) {
    val isExpire: Boolean
        get() {
            val diff = created!!.time - Calendar.getInstance().time.time
            return registered != null && !registered && abs(diff) / 1000L / 60L / 60L / 24L >= expireDay
        }

    fun isRegistered(): Boolean {
        return registered != null && registered
    }

    companion object {
        var expireDay = 10
    }
}
