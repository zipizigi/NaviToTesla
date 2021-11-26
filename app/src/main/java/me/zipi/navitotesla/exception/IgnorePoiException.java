package me.zipi.navitotesla.exception;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class IgnorePoiException extends RuntimeException {
    private final String packageName;
}
