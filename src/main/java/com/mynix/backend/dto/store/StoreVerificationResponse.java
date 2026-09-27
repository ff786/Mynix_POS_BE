package com.mynix.backend.dto.store;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StoreVerificationResponse {
    /** SENT, TOO_SOON or TOO_MANY (sending); VERIFIED or INVALID (checking). */
    private String status;
    /** Single-use proof of the verified number (VERIFIED only). */
    private String verificationToken;
    /** Purpose ACCOUNT only: whether this number already has a website account. */
    private Boolean accountExists;
    /** Purpose ACCOUNT only: the name the shop already has for this number, if any. */
    private String existingCustomerName;
}
