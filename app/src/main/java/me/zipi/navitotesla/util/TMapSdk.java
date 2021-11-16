package me.zipi.navitotesla.util;

import android.content.Context;

import com.skt.Tmap.TMapTapi;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.zipi.navitotesla.BuildConfig;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TMapSdk {
    public static void init(Context context) {
        new TMapTapi(context).setSKTMapAuthentication(BuildConfig.TMAP_API_KEY);
    }
}
