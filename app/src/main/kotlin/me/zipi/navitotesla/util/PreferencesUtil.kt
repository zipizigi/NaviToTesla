package me.zipi.navitotesla.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import me.zipi.navitotesla.model.Token
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Calendar

object PreferencesUtil {
    private const val preferencesFileName = "settings"

    @Throws(GeneralSecurityException::class, IOException::class)
    private fun getSharedPreferences(context: Context?): SharedPreferences {
        val masterKey = MasterKey.Builder(context!!)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context,
            preferencesFileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun remove(context: Context?, key: String?): Boolean {
        return try {
            getSharedPreferences(context).edit().remove(key).apply()
            true
        } catch (e: Exception) {
            Log.w(PreferencesUtil::class.java.name, "remove  error", e)
            AnalysisUtil.recordException(e)
            false
        }
    }

    fun clear(context: Context?) {
        try {
            getSharedPreferences(context).edit().clear().apply()
        } catch (e: Exception) {
            Log.w(PreferencesUtil::class.java.name, "clear error", e)
            AnalysisUtil.recordException(e)
        }
    }

    fun put(context: Context?, key: String?, value: Boolean?): Boolean {
        return try {
            getSharedPreferences(context).edit().putBoolean(key, value!!).apply()
            true
        } catch (e: Exception) {
            Log.w(PreferencesUtil::class.java.name, "put boolean error", e)
            AnalysisUtil.recordException(e)
            false
        }
    }

    fun put(context: Context?, key: String?, value: Long?): Boolean {
        return try {
            getSharedPreferences(context).edit().putLong(key, value!!).apply()
            true
        } catch (e: Exception) {
            Log.w(PreferencesUtil::class.java.name, "put long error", e)
            AnalysisUtil.recordException(e)
            false
        }
    }

    fun put(context: Context?, key: String?, value: String?): Boolean {
        return try {
            getSharedPreferences(context).edit().putString(key, value).apply()
            true
        } catch (e: Exception) {
            Log.w(PreferencesUtil::class.java.name, "put string error", e)
            AnalysisUtil.recordException(e)
            false
        }
    }

    fun getString(context: Context?, key: String?, defaultValue: String?): String? {
        return try {
            getSharedPreferences(context).getString(key, defaultValue)
        } catch (e: Exception) {
            Log.w(PreferencesUtil::class.java.name, "get string error", e)
            AnalysisUtil.recordException(e)
            defaultValue
        }
    }

    fun getString(context: Context?, key: String?): String? {
        return getString(context, key, null)
    }

//    fun getBoolean(context: Context?, key: String?): Boolean? {
//        return getBoolean(context, key, null)
//    }

    fun getBoolean(context: Context?, key: String?, defaultValue: Boolean): Boolean {
        return try {
            getSharedPreferences(context).getBoolean(key, defaultValue)
        } catch (e: Exception) {
            Log.w(PreferencesUtil::class.java.name, "get boolean error", e)
            AnalysisUtil.recordException(e)
            defaultValue
        }
    }

    fun getLong(context: Context?, key: String?): Long? {
        return try {
            val result = getSharedPreferences(context).getLong(key, -1)
            if (result == -1L) null else result
        } catch (e: Exception) {
            Log.w(PreferencesUtil::class.java.name, "get long error", e)
            AnalysisUtil.recordException(e)
            null
        }
    }

    fun getLong(context: Context?, key: String?, defaultValue: Long): Long {
        return try {
            getSharedPreferences(context).getLong(key, defaultValue)
        } catch (e: Exception) {
            Log.w(PreferencesUtil::class.java.name, "get long error", e)
            AnalysisUtil.recordException(e)
            defaultValue
        }
    }

    fun saveToken(context: Context?, token: Token) {
        put(context, "refreshToken", token.refreshToken)
        put(context, "accessToken", token.accessToken)
        put(context, "tokenUpdated", Calendar.getInstance().time.time)
    }

    fun expireToken(context: Context?) {
        if (getString(context, "refreshToken") != null) {
            put(context, "tokenUpdated", 0L)
        }
    }

    fun loadToken(context: Context?): Token? {
        return if (getString(context, "refreshToken") != null) {
            Token(
                refreshToken = getString(context, "refreshToken")!!,
                accessToken = getString(context, "accessToken")!!,
                updated = getLong(context, "tokenUpdated")!!,
            )
        } else null
    }
}