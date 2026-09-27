package com.mynix.backend.exception;

/** Store API: the requested order doesn't exist (or the phone number doesn't match). */
public class StoreNotFoundException extends RuntimeException {

    public StoreNotFoundException(String message) {
        super(message);
    }
}
