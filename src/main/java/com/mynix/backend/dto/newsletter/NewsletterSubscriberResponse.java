package com.mynix.backend.dto.newsletter;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NewsletterSubscriberResponse {
    private Long id;
    private String email;
    private LocalDateTime subscribedAt;
}
