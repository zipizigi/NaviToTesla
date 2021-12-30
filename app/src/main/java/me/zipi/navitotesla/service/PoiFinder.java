package me.zipi.navitotesla.service;

import java.io.IOException;
import java.util.List;

import me.zipi.navitotesla.exception.DuplicatePoiException;
import me.zipi.navitotesla.model.Poi;

public interface PoiFinder {
    default String findPoiAddress(String poiName) throws DuplicatePoiException, IOException {
        List<Poi> listPoi = listPoiAddress(poiName);
        String address = "";
        int sameCount = 0;
        for (Poi poi : listPoi) {
            if (poi.getPoiName().equalsIgnoreCase(poiName)) {
                sameCount++;
                address = poi.getFinalAddress();
            }
        }
        if (sameCount > 1) {
            // 중복지명 전송 안함
            throw new DuplicatePoiException(poiName);
        }

        return address;
    }

    String parseDestination(String notificationText);

    List<Poi> listPoiAddress(String poiName) throws IOException;

    boolean isIgnore(String notificationTitle, String notificationText);
}
