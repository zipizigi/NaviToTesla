package me.zipi.navitotesla.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Calendar;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import me.zipi.navitotesla.model.Token;

public class PreferencesUtil {
    private final static String preferencesFileName = "settings.xml";


    private static SharedPreferences getSharedPreferences(Context context) throws GeneralSecurityException, IOException {
        String mainKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

        return EncryptedSharedPreferences.create(
                preferencesFileName,
                mainKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    public static boolean remove(Context context, String key) {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences(context);

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(key);
            editor.apply();
            return true;
        } catch (Exception e) {
            Log.w(PreferencesUtil.class.getName(), "remove  error", e);
            return false;
        }
    }

    public static boolean put(Context context, String key, String value) {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences(context);

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(key, value);
            editor.apply();
            return true;
        } catch (Exception e) {
            Log.w(PreferencesUtil.class.getName(), "put string error", e);
            return false;
        }
    }

    public static String getString(Context context, String key, String defaultValue) {
        try {
            return getSharedPreferences(context).getString(key, defaultValue);
        } catch (Exception e) {
            Log.w(PreferencesUtil.class.getName(), "get string error", e);
            return defaultValue;
        }
    }

    public static String getString(Context context, String key) {
        return getString(context, key, null);
    }

    public static boolean put(Context context, String key, Long value) {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences(context);

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putLong(key, value);
            editor.apply();
            return true;
        } catch (Exception e) {
            Log.w(PreferencesUtil.class.getName(), "put long error", e);
            return false;
        }
    }

    public static Long getLong(Context context, String key) {
        try {
            long result = getSharedPreferences(context).getLong(key, -1);
            return result == -1 ? null : result;
        } catch (Exception e) {
            Log.w(PreferencesUtil.class.getName(), "get long error", e);
            return null;
        }
    }

    public static Long getLong(Context context, String key, Long defaultValue) {
        try {
            return getSharedPreferences(context).getLong(key, defaultValue);
        } catch (Exception e) {
            Log.w(PreferencesUtil.class.getName(), "get long error", e);
            return defaultValue;
        }
    }


    public static void saveToken(Context context, Token token) {
        put(context, "refreshToken", token.getRefreshToken());
        put(context, "accessToken", token.getAccessToken());
        put(context, "tokenUpdated", Calendar.getInstance().getTime().getTime());
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
