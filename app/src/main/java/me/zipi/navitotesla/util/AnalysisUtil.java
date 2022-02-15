package me.zipi.navitotesla.util;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalysisUtil {
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static final FirebaseCrashlytics firebaseCrashlytics = FirebaseCrashlytics.getInstance();
    private static FirebaseAnalytics firebaseAnalytics;

    public static void initialize(Context context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context);
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

    public static void recordException(Throwable e) {
        AnalysisUtil.firebaseCrashlytics.recordException(e);

        try (PrintWriter writer = new PrintWriter(new StringWriter())) {
            e.printStackTrace(writer);
            appendLog("WARN", e.toString() + System.lineSeparator() + writer.toString());
        }
    }

    public static void setCustomKey(String key, String value) {
        AnalysisUtil.firebaseCrashlytics.setCustomKey(key, value);
    }

    public static void sendUnsentReports() {
        AnalysisUtil.firebaseCrashlytics.sendUnsentReports();
    }

    public static String getLogFilePath() {
        File document = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        return new File(document.toString() + "/NaviToTesla.log").toString();
    }

    private static void appendLog(String logLevel, String message) {
        Log.i(AnalysisUtil.class.getName(), message);
        String dateTime = dateFormatter.format(Calendar.getInstance().getTime());
        String text = String.format("%s %s %s", dateTime, logLevel, message);

        File document = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        if (!document.exists() && !document.mkdirs()) {
            firebaseCrashlytics.log("create document directory fail");
            return;
        }

        File file = new File(document.toString() + "/NaviToTesla.log");

        try (BufferedWriter buf = new BufferedWriter(new FileWriter(file, true))) {
            buf.append(text);
            buf.newLine();
        } catch (FileNotFoundException ignore) {
        } catch (IOException e) {
            firebaseCrashlytics.recordException(e);
        }
    }

    public static long getLogFileSize() {
        File document = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File file = new File(document.toString() + "/NaviToTesla.log");
        if (!file.exists()) {
            return 0L;
        }
        return file.length();
    }

    public static void deleteLogFile() {
        File document = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File file = new File(document.toString() + "/NaviToTesla.log");
        if (file.exists()) {
            file.delete();
        }
    }
}
