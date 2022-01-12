package me.zipi.navitotesla.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Calendar;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import me.zipi.navitotesla.model.Token;

public class PreferencesUtil {
    private final static String preferencesFileName = "settings";

    private static SharedPreferences getSharedPreferences(Context context) throws GeneralSecurityException, IOException {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
        return EncryptedSharedPreferences.create(
                context,
                preferencesFileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    public static boolean remove(Context context, String key) {
        try {
            getSharedPreferences(context).edit().remove(key).apply();
            return true;
        } catch (Exception e) {
            Log.w(PreferencesUtil.class.getName(), "remove  error", e);
            AnalysisUtil.recordException(e);
            return false;
        }
    }


    public static void clear(Context context) {
        try {
            getSharedPreferences(context).edit().clear().apply();
        } catch (Exception e) {
            Log.w(PreferencesUtil.class.getName(), "clear error", e);
            AnalysisUtil.recordException(e);
        }

    }

    public static boolean put(Context context, String key, String value) {
        try {
            getSharedPreferences(context).edit().putString(key, value).apply();
            return true;
        } catch (Exception e) {
            Log.w(PreferencesUtil.class.getName(), "put string error", e);
            AnalysisUtil.recordException(e);
            return false;
        }
    }

    public static String getString(Context context, String key, String defaultValue) {
        try {
            return getSharedPreferences(context).getString(key, defaultValue);
        } catch (Exception e) {
            Log.w(PreferencesUtil.class.getName(), "get string error", e);
            AnalysisUtil.recordException(e);
            return defaultValue;
        }
    }

    public static String getString(Context context, String key) {
        return getString(context, key, null);
    }

    public static boolean put(Context context, String key, Long value) {
        try {
            getSharedPreferences(context).edit().putLong(key, value).apply();
            return true;
        } catch (Exception e) {
            Log.w(PreferencesUtil.class.getName(), "put long error", e);
            AnalysisUtil.recordException(e);
            return false;
        }
    }

    public static Long getLong(Context context, String key) {
        try {
            long result = getSharedPreferences(context).getLong(key, -1);
            return result == -1 ? null : result;
        } catch (Exception e) {
            Log.w(PreferencesUtil.class.getName(), "get long error", e);
            AnalysisUtil.recordException(e);
            return null;
        }
    }

    public static Long getLong(Context context, String key, Long defaultValue) {
        try {
            return getSharedPreferences(context).getLong(key, defaultValue);
        } catch (Exception e) {
            Log.w(PreferencesUtil.class.getName(), "get long error", e);
            AnalysisUtil.recordException(e);
            return defaultValue;
        }
    }


    public static void saveToken(Context context, Token token) {
        put(context, "refreshToken", token.getRefreshToken());
        put(context, "accessToken", token.getAccessToken());
        put(context, "tokenUpdated", Calendar.getInstance().getTime().getTime());
    }

    public static void expireToken(Context context) {
        if (getString(context, "refreshToken") != null) {
            put(context, "tokenUpdated", 0L);
        }
    }

    public static Token loadToken(Context context) {
        if (getString(context, "refreshToken") != null) {
            return Token.builder()
                    .refreshToken(getString(context, "refreshToken"))
                    .accessToken(getString(context, "accessToken"))
                    .updated(getLong(context, "tokenUpdated"))
                    .build();
        }
        return null;
    }

}
