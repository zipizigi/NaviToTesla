package me.zipi.navitotesla.exception;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class RateLimitException extends RuntimeException {
    String message;
}
