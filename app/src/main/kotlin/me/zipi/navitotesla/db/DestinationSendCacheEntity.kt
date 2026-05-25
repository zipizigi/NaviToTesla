package me.zipi.navitotesla.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import kotlin.math.abs

@Entity(
    tableName = "destination_send_cache",
    indices = [Index(value = ["poi", "packageName"], unique = true)],
)
class DestinationSendCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null,
    val poi: String,
    val sentAddress: String,
    val sentAsJibun: Boolean,
    val packageName: String,
    val created: Date,
) {
    val isExpire: Boolean
        get() {
            val ageInDays = abs(System.currentTimeMillis() - created.time) / 1000L / 60L / 60L / 24L
            return ageInDays >= EXPIRE_DAY
        }

    companion object {
        const val EXPIRE_DAY = 30
    }
}
