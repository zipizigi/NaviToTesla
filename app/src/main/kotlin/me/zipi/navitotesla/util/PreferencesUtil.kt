package me.zipi.navitotesla.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zipi.navitotesla.model.Token
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Calendar

object PreferencesUtil {
    private lateinit var instance: SharedPreferences

    @Throws(GeneralSecurityException::class, IOException::class)
    fun initialize(applicationContext: Context) {
        if (!this::instance.isInitialized) {
            synchronized(PreferencesUtil::class) {
                val masterKey = MasterKey.Builder(applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                instance = EncryptedSharedPreferences.create(
                    applicationContext,
                    preferencesFileName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }
        }
    }

    private const val preferencesFileName = "settings"

    suspend fun remove(key: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                instance.edit().remove(key).apply()
                true
            } catch (e: Exception) {
                Log.w(PreferencesUtil::class.java.name, "remove  error", e)
                AnalysisUtil.recordException(e)
                false
            }
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            try {
                instance.edit().clear().apply()
            } catch (e: Exception) {
                Log.w(PreferencesUtil::class.java.name, "clear error", e)
                AnalysisUtil.recordException(e)
            }
        }
    }

    suspend fun put(key: String, value: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                instance.edit().putBoolean(key, value).apply()
                true
            } catch (e: Exception) {
                Log.w(PreferencesUtil::class.java.name, "put boolean error", e)
                AnalysisUtil.recordException(e)
                false
            }
        }
    }

    suspend fun put(key: String, value: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                instance.edit().putLong(key, value).apply()
                true
            } catch (e: Exception) {
                Log.w(PreferencesUtil::class.java.name, "put long error", e)
                AnalysisUtil.recordException(e)
                false
            }
        }
    }

    suspend fun put(key: String, value: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                instance.edit().putString(key, value).apply()
                true
            } catch (e: Exception) {
                Log.w(PreferencesUtil::class.java.name, "put string error", e)
                AnalysisUtil.recordException(e)
                false
            }
        }
    }

    suspend fun getString(key: String, defaultValue: String?): String? {
        return withContext(Dispatchers.IO) {
            try {
                instance.getString(key, defaultValue)
            } catch (e: Exception) {
                Log.w(PreferencesUtil::class.java.name, "get string error", e)
                AnalysisUtil.recordException(e)
                defaultValue
            }
        }
    }

    fun getStringSync(key: String, defaultValue: String?): String? {
        return try {
            instance.getString(key, defaultValue)
        } catch (e: Exception) {
            Log.w(PreferencesUtil::class.java.name, "get string error", e)
            AnalysisUtil.recordException(e)
            defaultValue
        }

    }

    suspend fun getString(key: String): String? {
        return getString(key, null)
    }

//    fun getBoolean(context: Context?, key: String?): Boolean? {
//        return getBoolean(context, key, null)
//    }

    suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                instance.getBoolean(key, defaultValue)
            } catch (e: Exception) {
                Log.w(PreferencesUtil::class.java.name, "get boolean error", e)
                AnalysisUtil.recordException(e)
                defaultValue
            }
        }
    }

    suspend fun getLong(key: String): Long? {
        return withContext(Dispatchers.IO) {
            try {
                val result = instance.getLong(key, -1)
                if (result == -1L) null else result
            } catch (e: Exception) {
                Log.w(PreferencesUtil::class.java.name, "get long error", e)
                AnalysisUtil.recordException(e)
                null
            }
        }
    }

    fun getLongSync(key: String): Long? {
        return try {
            val result = instance.getLong(key, -1)
            if (result == -1L) null else result
        } catch (e: Exception) {
            Log.w(PreferencesUtil::class.java.name, "get long error", e)
            AnalysisUtil.recordException(e)
            null
        }

    }

    suspend fun getLong(key: String, defaultValue: Long): Long {
        return withContext(Dispatchers.IO) {
            try {
                instance.getLong(key, defaultValue)
            } catch (e: Exception) {
                Log.w(PreferencesUtil::class.java.name, "get long error", e)
                AnalysisUtil.recordException(e)
                defaultValue
            }
        }
    }

    suspend fun saveToken(token: Token) {
        put("refreshToken", token.refreshToken)
        put("accessToken", token.accessToken)
        put("tokenUpdated", Calendar.getInstance().time.time)
    }

    suspend fun expireToken() {
        if (getString("refreshToken") != null) {
            put("tokenUpdated", 0L)
        }
    }

    suspend fun loadToken(): Token? {
        return if (getString("refreshToken") != null) {
            Token(
                refreshToken = getString("refreshToken")!!,
                accessToken = getString("accessToken")!!,
                updated = getLong("tokenUpdated")!!,
            )
        } else null
    }

    fun loadTokenSync(): Token? {
        return if (getStringSync("refreshToken", null) != null) {
            Token(
                refreshToken = getStringSync("refreshToken", null)!!,
                accessToken = getStringSync("accessToken", null)!!,
                updated = getLongSync("tokenUpdated")!!,
            )
        } else null
    }
}