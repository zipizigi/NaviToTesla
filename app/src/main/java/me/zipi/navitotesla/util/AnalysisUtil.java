package me.zipi.navitotesla.util;

import android.content.Context;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalysisUtil {
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
    }

    public static void recordException(Throwable e) {
        AnalysisUtil.firebaseCrashlytics.recordException(e);
    }

    public static void setCustomKey(String key, String value) {
        AnalysisUtil.firebaseCrashlytics.setCustomKey(key, value);
    }

    public static void sendUnsentReports() {
        AnalysisUtil.firebaseCrashlytics.sendUnsentReports();
    }
}
