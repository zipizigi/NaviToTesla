package me.zipi.navitotesla.service.place

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.RemoteConfigUtil
import java.security.MessageDigest
import java.util.Date

sealed class PlaceAutocompleteCacheEntry {
    object Searchable : PlaceAutocompleteCacheEntry()

    object NotSearchable : PlaceAutocompleteCacheEntry()
}

interface PlaceAutocompleteCacheClient {
    /** null = miss (분석된 적 없거나 조회 실패). */
    suspend fun lookup(address: String): PlaceAutocompleteCacheEntry?

    suspend fun cache(
        address: String,
        searchable: Boolean,
    )
}

object FirestorePlaceAutocompleteCacheClient : PlaceAutocompleteCacheClient {
    private const val COLLECTION = "place_autocomplete_cache"
    private const val FIELD_SEARCHABLE = "searchable"
    private const val FIELD_EXPIRES_AT = "expiresAt"
    private const val DEFAULT_TTL_DAYS = 30L

    override suspend fun lookup(address: String): PlaceAutocompleteCacheEntry? =
        try {
            val doc =
                FirebaseFirestore
                    .getInstance()
                    .collection(COLLECTION)
                    .document(hash(address))
                    .get()
                    .await()
            if (!doc.exists()) {
                null
            } else {
                when (doc.getBoolean(FIELD_SEARCHABLE)) {
                    true -> PlaceAutocompleteCacheEntry.Searchable
                    false -> PlaceAutocompleteCacheEntry.NotSearchable
                    null -> null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AnalysisUtil.recordException(e)
            null
        }

    override suspend fun cache(
        address: String,
        searchable: Boolean,
    ) {
        try {
            val ttlDays = RemoteConfigUtil.getLong(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_TTL_DAYS).takeIf { it > 0L } ?: DEFAULT_TTL_DAYS
            val expiresAt = Timestamp(Date(System.currentTimeMillis() + ttlDays * 24L * 60L * 60L * 1000L))
            FirebaseFirestore
                .getInstance()
                .collection(COLLECTION)
                .document(hash(address))
                .set(
                    mapOf(
                        FIELD_SEARCHABLE to searchable,
                        FIELD_EXPIRES_AT to expiresAt,
                    ),
                ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AnalysisUtil.recordException(e)
        }
    }

    private fun hash(address: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(address.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
