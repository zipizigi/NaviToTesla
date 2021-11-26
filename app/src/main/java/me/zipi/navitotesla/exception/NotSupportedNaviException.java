package me.zipi.navitotesla.exception;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class NotSupportedNaviException extends RuntimeException {
    private final String packageName;
}
