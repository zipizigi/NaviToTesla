package me.zipi.navitotesla.util;

import android.content.Context;

import com.google.firebase.analytics.FirebaseAnalytics;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalysisUtil {
    @Getter
    private static FirebaseAnalytics firebaseAnalytics;


    public static void initialize(Context context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context);
    }
}
