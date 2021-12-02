package me.zipi.navitotesla.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.security.GeneralSecurityException;
import java.util.Map;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Will remove....
 */
public class PreferencesMigrationUtil {
    private final static String preferencesFileName = "settings";
    private final static String oldPreferencesFileName = "settings.xml";

    private static SharedPreferences getSharedPreferences(Context context, String fileName) throws GeneralSecurityException, IOException {

        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
        return EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    public static void migration(Context context) {
        String filePrefix = context.getFilesDir().getParent() + "/shared_prefs/";
        File file = new File(filePrefix + oldPreferencesFileName + ".xml");
        if (!file.exists()) {
            Log.i(PreferencesMigrationUtil.class.getName(), "migration already process. Skip....");
            return;
        }
        Log.i(PreferencesMigrationUtil.class.getName(), "migration start..");
        if (new File(filePrefix + preferencesFileName + ".xml").exists()) {
            AnalysisUtil.getFirebaseCrashlytics().log("migration warning. file already exists");
            AnalysisUtil.getFirebaseCrashlytics()
                    .recordException(new Exception("File already exists: " + preferencesFileName + ".xml"));
            return;
        }
        try {
            Map<String, ?> settings = getSharedPreferences(context, oldPreferencesFileName).getAll();
            SharedPreferences.Editor editor = getSharedPreferences(context, preferencesFileName).edit();
            for (String key : settings.keySet()) {
                Object value = settings.get(key);
                if (value instanceof String) {
                    editor.putString(key, (String) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Float) {
                    editor.putFloat(key, (Float) value);
                }
            }

            editor.apply();
            file.delete();
        } catch (Exception e) {
            Log.w(PreferencesMigrationUtil.class.getName(), "error migration", e);
            AnalysisUtil.getFirebaseCrashlytics().log("error in migration");
            AnalysisUtil.getFirebaseCrashlytics().recordException(e);
        }
    }


}
