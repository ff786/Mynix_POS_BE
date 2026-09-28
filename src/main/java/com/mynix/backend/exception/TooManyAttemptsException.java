package com.mynix.backend.exception;

/** Login refused because of too many recent failed attempts (HTTP 429). */
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String message) {
        super(message);
    }
}
