package com.mynix.backend.dto.newsletter;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NewsletterSignupRequest {

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    /** SHA-256 (hex) of the visitor's IP, computed by the website. */
    @NotBlank
    @Pattern(regexp = "^[a-f0-9]{64}$")
    private String visitorHash;
}
