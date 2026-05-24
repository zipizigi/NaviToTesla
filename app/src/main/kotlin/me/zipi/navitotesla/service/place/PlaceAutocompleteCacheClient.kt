package me.zipi.navitotesla.service.place

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import me.zipi.navitotesla.util.AnalysisUtil
import java.security.MessageDigest

/**
 * 캐시 조회 결과.
 *   - null = 캐시 miss (분석된 적 없거나 조회 실패)
 *   - Searchable = positive (자동완성 매치 있음 → 도로명 전송)
 *   - NotSearchable = negative (자동완성 매치 없음 → 구주소 전송)
 */
sealed class PlaceAutocompleteCacheEntry {
    object Searchable : PlaceAutocompleteCacheEntry()

    object NotSearchable : PlaceAutocompleteCacheEntry()
}

interface PlaceAutocompleteCacheClient {
    suspend fun lookup(address: String): PlaceAutocompleteCacheEntry?

    suspend fun cache(
        address: String,
        searchable: Boolean,
    )
}

object FirestorePlaceAutocompleteCacheClient : PlaceAutocompleteCacheClient {
    private const val COLLECTION = "place_autocomplete_cache"
    private const val FIELD_SEARCHABLE = "searchable"
    private const val FIELD_CREATED_AT = "createdAt"

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
                val searchable = doc.getBoolean(FIELD_SEARCHABLE)
                when (searchable) {
                    true -> PlaceAutocompleteCacheEntry.Searchable
                    false -> PlaceAutocompleteCacheEntry.NotSearchable
                    null -> null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AnalysisUtil.log("FirestorePlaceAutocompleteCacheClient.lookup failed: ${e.javaClass.simpleName}")
            AnalysisUtil.recordException(e)
            null
        }

    override suspend fun cache(
        address: String,
        searchable: Boolean,
    ) {
        try {
            FirebaseFirestore
                .getInstance()
                .collection(COLLECTION)
                .document(hash(address))
                .set(
                    mapOf(
                        FIELD_SEARCHABLE to searchable,
                        FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                    ),
                ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AnalysisUtil.log("FirestorePlaceAutocompleteCacheClient.cache failed: ${e.javaClass.simpleName}")
            AnalysisUtil.recordException(e)
        }
    }

    private fun hash(address: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(address.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
