package me.zipi.navitotesla.service.poifinder;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.zipi.navitotesla.exception.NotSupportedNaviException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PoiFinderFactory {

    private static final String tmapPackage = "com.skt.tmap.ku";
    private static final String tmapSKPackage = "com.skt.skaf.l001mtm091";
    private static final String kakaoPackage = "com.locnall.KimGiSa";

    public static boolean isNaviSupport(String packageName) {
        return packageName.equalsIgnoreCase(tmapPackage) || packageName.equalsIgnoreCase(tmapSKPackage)
                || packageName.equalsIgnoreCase(kakaoPackage);
    }

    public static PoiFinder getPoiFinder(String packageName) throws NotSupportedNaviException {
        if (packageName.equalsIgnoreCase(tmapPackage) || packageName.equalsIgnoreCase(tmapSKPackage)) {
            return new TMapPoiFinder();
        } else if (packageName.equalsIgnoreCase(kakaoPackage)) {
            return new KakaoPoiFinder();
        }
        throw new NotSupportedNaviException(packageName);
    }

    public static PoiFinder getKakaoPoiFinder() {
        return new KakaoPoiFinder();
    }

    public static PoiFinder getTMapPoiFinder() {
        return new TMapPoiFinder();
    }
}
