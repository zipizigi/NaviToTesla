package me.zipi.navitotesla.exception;

import androidx.annotation.NonNull;

public class ForbiddenException extends RuntimeException {
    private final Integer httpCode;

    public ForbiddenException(Integer httpCode, String message) {
        super(message);
        this.httpCode = httpCode;
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() + " httpStatus: " + httpCode;
    }
}
