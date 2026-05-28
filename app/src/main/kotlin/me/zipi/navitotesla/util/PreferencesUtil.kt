package me.zipi.navitotesla.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zipi.navitotesla.model.SendMode
import me.zipi.navitotesla.model.Token
import java.util.concurrent.CountDownLatch

object PreferencesUtil {
    private const val PREFERENCES_FILE_NAME = "settings"
    private const val KEY_DEFAULT_SEND_MODE = "defaultSendMode"
    private const val KEY_FALLBACK_SEND_MODE = "fallbackSendMode"

    @Volatile
    private var instance: SharedPreferences? = null
    private val initLatch = CountDownLatch(1)

    suspend fun initialize(applicationContext: Context) {
        if (instance != null) return
        try {
            withContext(Dispatchers.IO) {
                val masterKey =
                    MasterKey
                        .Builder(applicationContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                instance =
                    EncryptedSharedPreferences.create(
                        applicationContext,
                        PREFERENCES_FILE_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                    )
            }
        } catch (e: Exception) {
            AnalysisUtil.recordException(e)
        } finally {
            initLatch.countDown()
        }
    }

    private fun prefs(): SharedPreferences {
        instance?.let { return it }
        initLatch.await()
        return instance ?: error("PreferencesUtil initialization failed")
    }

    suspend fun remove(key: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                prefs().edit { remove(key) }
                true
            } catch (e: Exception) {
                AnalysisUtil.warn("remove  error", e)
                AnalysisUtil.recordException(e)
                false
            }
        }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            try {
                prefs().edit { clear() }
            } catch (e: Exception) {
                AnalysisUtil.warn("clear error", e)
                AnalysisUtil.recordException(e)
            }
        }
    }

    suspend fun put(
        key: String,
        value: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                prefs().edit { putBoolean(key, value) }
                true
            } catch (e: Exception) {
                AnalysisUtil.warn("put boolean error", e)
                AnalysisUtil.recordException(e)
                false
            }
        }

    suspend fun put(
        key: String,
        value: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                prefs().edit { putLong(key, value) }
                true
            } catch (e: Exception) {
                AnalysisUtil.warn("put long error", e)
                AnalysisUtil.recordException(e)
                false
            }
        }

    suspend fun put(
        key: String,
        value: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                prefs().edit { putString(key, value) }
                true
            } catch (e: Exception) {
                AnalysisUtil.warn("put string error", e)
                AnalysisUtil.recordException(e)
                false
            }
        }

    suspend fun getString(
        key: String,
        defaultValue: String?,
    ): String? =
        withContext(Dispatchers.IO) {
            try {
                prefs().getString(key, defaultValue)
            } catch (e: Exception) {
                AnalysisUtil.warn("get string error", e)
                AnalysisUtil.recordException(e)
                defaultValue
            }
        }

    fun getStringSync(
        key: String,
        defaultValue: String?,
    ): String? =
        try {
            prefs().getString(key, defaultValue)
        } catch (e: Exception) {
            AnalysisUtil.warn("get string error", e)
            AnalysisUtil.recordException(e)
            defaultValue
        }

    suspend fun getString(key: String): String? = getString(key, null)

    suspend fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                prefs().getBoolean(key, defaultValue)
            } catch (e: Exception) {
                AnalysisUtil.warn("get boolean error", e)
                AnalysisUtil.recordException(e)
                defaultValue
            }
        }

    suspend fun getLong(key: String): Long? =
        withContext(Dispatchers.IO) {
            try {
                val result = prefs().getLong(key, -1)
                if (result == -1L) null else result
            } catch (e: Exception) {
                AnalysisUtil.warn("get long error", e)
                AnalysisUtil.recordException(e)
                null
            }
        }

    fun getLongSync(key: String): Long? =
        try {
            val result = prefs().getLong(key, -1)
            if (result == -1L) null else result
        } catch (e: Exception) {
            AnalysisUtil.warn("get long error", e)
            AnalysisUtil.recordException(e)
            null
        }

    suspend fun getLong(
        key: String,
        defaultValue: Long,
    ): Long =
        withContext(Dispatchers.IO) {
            try {
                prefs().getLong(key, defaultValue)
            } catch (e: Exception) {
                AnalysisUtil.warn("get long error", e)
                AnalysisUtil.recordException(e)
                defaultValue
            }
        }

    suspend fun saveToken(token: Token) {
        put("refreshToken", token.refreshToken)
        put("accessToken", token.accessToken)
        put("tokenUpdated", System.currentTimeMillis())
    }

    suspend fun expireToken() {
        if (getString("refreshToken") != null) {
            put("tokenUpdated", 0L)
        }
    }

    suspend fun loadToken(): Token? =
        if (getString("refreshToken") != null) {
            Token(
                refreshToken = getString("refreshToken")!!,
                accessToken = getString("accessToken")!!,
                updated = getLong("tokenUpdated")!!,
            )
        } else {
            null
        }

    fun loadTokenSync(): Token? =
        if (getStringSync("refreshToken", null) != null) {
            Token(
                refreshToken = getStringSync("refreshToken", null)!!,
                accessToken = getStringSync("accessToken", null)!!,
                updated = getLongSync("tokenUpdated")!!,
            )
        } else {
            null
        }

    suspend fun getDefaultSendMode(): SendMode = getSendMode(KEY_DEFAULT_SEND_MODE)

    suspend fun getFallbackSendMode(): SendMode = getSendMode(KEY_FALLBACK_SEND_MODE)

    suspend fun setDefaultSendMode(mode: SendMode) {
        put(KEY_DEFAULT_SEND_MODE, mode.name.lowercase())
    }

    suspend fun setFallbackSendMode(mode: SendMode) {
        put(KEY_FALLBACK_SEND_MODE, mode.name.lowercase())
    }

    private suspend fun getSendMode(key: String): SendMode {
        val raw = getString(key, SendMode.ROAD.name.lowercase()) ?: return SendMode.ROAD
        return runCatching { SendMode.valueOf(raw.uppercase()) }
            .getOrElse { SendMode.ROAD }
            .takeIf { it != SendMode.GPS } // GPS 는 사용자 설정에 노출되지 않음
            ?: SendMode.ROAD
    }
}
