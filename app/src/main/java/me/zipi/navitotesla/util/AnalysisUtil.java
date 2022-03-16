package me.zipi.navitotesla.util;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import androidx.annotation.Nullable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalysisUtil {
    private static final FirebaseCrashlytics firebaseCrashlytics = FirebaseCrashlytics.getInstance();
    private static FirebaseAnalytics firebaseAnalytics;
    @Nullable
    private static String externalDir;

    public static void initialize(Context context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context);
        if (context.getExternalFilesDir(null) != null) {
            externalDir = context.getExternalFilesDir(null).toString();
        } else {
            externalDir = null;
        }
    }

    public static void logEvent(String event, Bundle param) {
        AnalysisUtil.firebaseAnalytics.logEvent(event, param);
    }

    public static void log(String message) {
        AnalysisUtil.firebaseCrashlytics.log(message);
        appendLog("INFO", message);
    }

    public static void info(String message) {
        AnalysisUtil.firebaseCrashlytics.log(message);
        appendLog("INFO", message);
    }

    public static void warn(String message) {
        AnalysisUtil.firebaseCrashlytics.log(message);
        appendLog("WARN", message);
    }

    public static void error(String message) {
        AnalysisUtil.firebaseCrashlytics.log(message);
        appendLog("ERROR", message);
    }

    public static boolean isWritableLog() {
        return externalDir != null;
    }

    public static void recordException(Throwable e) {
        AnalysisUtil.firebaseCrashlytics.recordException(e);

        try (PrintWriter writer = new PrintWriter(new StringWriter())) {
            e.printStackTrace(writer);
            appendLog("WARN", e + System.lineSeparator() + writer);
        }
    }

    public static void setCustomKey(String key, String value) {
        AnalysisUtil.firebaseCrashlytics.setCustomKey(key, value);
    }

    public static void sendUnsentReports() {
        AnalysisUtil.firebaseCrashlytics.sendUnsentReports();
    }

    public static String getLogFilePath() {
        return new File(externalDir + "/NaviToTesla.log").toString();
    }

    private static void appendLog(String logLevel, String message) {
        Log.i(AnalysisUtil.class.getName(), message);
        String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Calendar.getInstance().getTime());
        String text = String.format("%s %s %s", dateTime, logLevel, message);

        if (externalDir != null && !new File(externalDir).exists() && !new File(externalDir).mkdirs()) {
            firebaseCrashlytics.log("create document directory fail");
            return;
        }

        if (!isWritableLog()) {
            return;
        }
        File file = new File(externalDir + "/NaviToTesla.log");

        try (BufferedWriter buf = new BufferedWriter(new FileWriter(file, true))) {
            buf.append(text);
            buf.newLine();
        } catch (FileNotFoundException ignore) {
        } catch (IOException e) {
            firebaseCrashlytics.recordException(e);
        }
    }

    public static long getLogFileSize() {
        File file = new File(externalDir + "/NaviToTesla.log");
        if (!file.exists()) {
            return 0L;
        }
        return file.length();
    }

    public static void deleteLogFile() {
        File file = new File(externalDir + "/NaviToTesla.log");
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    public static void makeToast(Context context, String text) {
        try {
            Log.i(AnalysisUtil.class.getName(), text);
            AnalysisUtil.log(text);
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, text, Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            AnalysisUtil.recordException(e);
            e.printStackTrace();
        }

    }
}
