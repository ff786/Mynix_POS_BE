package com.mynix.backend.model;

public enum UserRole {
    ADMIN,
    CASHIER,
    /** The website's server account: can only read the catalogue and place online orders. */
    ONLINE_STORE
}