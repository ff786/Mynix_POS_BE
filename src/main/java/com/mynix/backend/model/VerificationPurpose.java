package com.mynix.backend.model;

/** Why a phone number is being verified; a code for one purpose can't be used for another. */
public enum VerificationPurpose {
    CHECKOUT,
    ACCOUNT
}
