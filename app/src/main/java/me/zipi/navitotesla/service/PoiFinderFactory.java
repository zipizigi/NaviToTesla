package me.zipi.navitotesla.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PoiFinderFactory {

    private static final String tmapPackage = "com.skt.tmap.ku";
    private static final String tmapSKPackage = "com.skt.skaf.l001mtm091";
    private static final String kakaoPackage = "com.locnall.KimGiSa";

    public static boolean isNaviSupport(String packageName) {
        return packageName.equalsIgnoreCase(tmapPackage) || packageName.equalsIgnoreCase(tmapSKPackage)
                || packageName.equalsIgnoreCase(kakaoPackage);
    }

    public static PoiFinder getPoiFinder(String packageName) {
        if (packageName.equalsIgnoreCase(tmapPackage) || packageName.equalsIgnoreCase(tmapSKPackage)) {
            return new TMapPoiFinder();
        } else if (packageName.equalsIgnoreCase(kakaoPackage)) {
            return new KakaoPoiFinder();
        }
        return null;
    }
}
