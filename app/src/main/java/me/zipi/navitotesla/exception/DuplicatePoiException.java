package me.zipi.navitotesla.exception;

public class DuplicatePoiException extends RuntimeException {
    public DuplicatePoiException(String poiName) {
        this.poiName = poiName;
    }

    String poiName;
}
