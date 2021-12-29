package me.zipi.navitotesla.service;

import java.io.IOException;
import java.util.List;

import me.zipi.navitotesla.exception.DuplicatePoiException;
import me.zipi.navitotesla.model.Poi;

public interface PoiFinder {
    String findPoiAddress(String poiName) throws DuplicatePoiException, IOException;

    String parseDestination(String notificationText);

    List<Poi> listPoiAddress(String poiName) throws IOException;

    boolean isIgnore(String notificationTitle, String notificationText);
}
