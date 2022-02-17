package me.zipi.navitotesla.exception;

import lombok.Getter;

public class DuplicatePoiException extends RuntimeException {
    @Getter
    private final String poiName;

    public DuplicatePoiException(String poiName) {
        this.poiName = poiName;
    }
}
