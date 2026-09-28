package com.mynix.backend.service.impl;

import com.mynix.backend.dto.newsletter.NewsletterSubscriberResponse;
import com.mynix.backend.repository.NewsletterSubscriberRepository;
import com.mynix.backend.service.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Newsletter sign-ups from the website: stored once per email, rate-limited
 * in the database, and each new subscriber is texted to the shop.
 */
@Service
@RequiredArgsConstructor
public class NewsletterService {

    static final int MAX_PER_VISITOR_PER_10_MIN = 5;
    static final int MAX_PER_HOUR = 100;

    public enum Result { SUBSCRIBED, ALREADY_SUBSCRIBED, THROTTLED }

    private final NewsletterSubscriberRepository repository;
    private final SmsService smsService;

    /** The shop's number for new-subscriber alerts (MYNIX_NEWSLETTER_NOTIFY_PHONE). */
    @Value("${mynix.newsletter.notify-phone:0778843815}")
    private String notifyPhone;

    @Transactional
    public Result subscribe(String email, String visitorHash) {

        String normalized = email.trim().toLowerCase();
        if (repository.existsByEmail(normalized)) {
            return Result.ALREADY_SUBSCRIBED;
        }

        LocalDateTime now = LocalDateTime.now();
        if (repository.countByVisitorHashAndCreatedAtAfter(visitorHash, now.minusMinutes(10)) >= MAX_PER_VISITOR_PER_10_MIN
                || repository.countByCreatedAtAfter(now.minusHours(1)) >= MAX_PER_HOUR) {
            return Result.THROTTLED;
        }

        if (repository.insertIfAbsent(normalized, visitorHash, now) == 0) {
            return Result.ALREADY_SUBSCRIBED; // two sign-ups for the same email at once
        }

        smsService.sendSms(notifyPhone, "MYNIX website: new newsletter subscriber\n" + normalized);
        return Result.SUBSCRIBED;
    }

    @Transactional(readOnly = true)
    public List<NewsletterSubscriberResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(s -> NewsletterSubscriberResponse.builder()
                        .id(s.getId())
                        .email(s.getEmail())
                        .subscribedAt(s.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
