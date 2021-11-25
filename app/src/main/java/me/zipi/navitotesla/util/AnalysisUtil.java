package me.zipi.navitotesla.util;

import android.content.Context;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalysisUtil {
    @Getter
    private static FirebaseAnalytics firebaseAnalytics;
    @Getter
    private static FirebaseCrashlytics firebaseCrashlytics = FirebaseCrashlytics.getInstance();


    public static void initialize(Context context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context);
    }
}
