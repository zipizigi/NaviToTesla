package me.zipi.navitotesla.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.model.SendMode
import java.util.Date
import kotlin.math.abs

@Entity(
    tableName = "poi_address",
    indices = [Index(value = ["poi", "packageName"], unique = true)],
)
class PoiAddressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null,
    val poi: String,
    val packageName: String? = null,
    val roadAddress: String? = null,
    val jibunAddress: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val registered: Boolean? = null,
    val isDuplicate: Boolean? = null,
    val sentMode: String? = null,
    val searchable: Boolean? = null,
    val created: Date? = null,
    // 마지막 classify 시도 시각(epoch millis). searchable=null 로 끝난 경우에도 갱신되어
    // 24h 쿨다운 동안 Places API/Firestore 재호출을 차단하는 anchor 로 쓰인다.
    val lastCheckedAt: Long? = null,
) {
    val isExpire: Boolean
        get() {
            val createdTime = created?.time ?: return false
            if (registered == true) return false
            val ageInDays = abs(System.currentTimeMillis() - createdTime) / 1000L / 60L / 60L / 24L
            val expireDays = if (isDuplicate == true) DUPLICATE_EXPIRE_DAY else EXPIRE_DAY
            return ageInDays >= expireDays
        }

    fun isRegistered(): Boolean = registered == true

    fun toPoi(): Poi =
        Poi(
            poiName = poi,
            roadAddress = roadAddress,
            address = jibunAddress,
            latitude = latitude,
            longitude = longitude,
            packageName = packageName ?: "",
            isDuplicate = isDuplicate == true,
            registeredSentMode = if (registered == true) toSendMode() else null,
        )

    // registered=true 인데 sentMode 가 null/unknown 인 corruption 케이스는 ROAD 로 안전 폴백.
    private fun toSendMode(): SendMode =
        when (sentMode) {
            SENT_MODE_JIBUN -> SendMode.JIBUN
            SENT_MODE_GPS -> SendMode.GPS
            else -> SendMode.ROAD
        }

    companion object {
        const val EXPIRE_DAY = 30
        const val DUPLICATE_EXPIRE_DAY = 1
        const val SENT_MODE_ROAD = "ROAD"
        const val SENT_MODE_JIBUN = "JIBUN"
        const val SENT_MODE_GPS = "GPS"
    }
}
