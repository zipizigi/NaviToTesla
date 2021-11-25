package me.zipi.navitotesla.service;

import java.io.IOException;

import me.zipi.navitotesla.exception.DuplicatePoiException;

public interface PoiFinder {
    String findPoiAddress(String poiName) throws DuplicatePoiException, IOException;

    String parseDestination(String notificationText);

    boolean isIgnore(String notificationTitle, String notificationText);
}
