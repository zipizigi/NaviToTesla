package me.zipi.navitotesla.exception;

public class DuplicatePoiException extends Exception {
    public DuplicatePoiException(String poiName){
        this.poiName = poiName;
    }
    String poiName;
}
