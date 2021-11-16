package me.zipi.navitotesla.service;

public interface PoiFinder {
    String findPoiAddress(String poiName);

    String parseDestination(String notificationText);

    boolean isIgnore(String notificationTitle, String notificationText);
}
