package me.zipi.navitotesla.exception;

public class DuplicatePoiException extends RuntimeException {
    String poiName;

    public DuplicatePoiException(String poiName) {
        this.poiName = poiName;
    }
}
