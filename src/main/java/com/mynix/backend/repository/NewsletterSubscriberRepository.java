package com.mynix.backend.repository;

import com.mynix.backend.model.NewsletterSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NewsletterSubscriberRepository extends JpaRepository<NewsletterSubscriber, Long> {

    boolean existsByEmail(String email);

    long countByVisitorHashAndCreatedAtAfter(String visitorHash, LocalDateTime after);

    long countByCreatedAtAfter(LocalDateTime after);

    List<NewsletterSubscriber> findAllByOrderByCreatedAtDesc();

    /** Returns 1 if added, 0 if the email was already subscribed (even at the same moment). */
    @Modifying
    @Query(value = """
        INSERT INTO newsletter_subscribers (email, visitor_hash, created_at)
        VALUES (:email, :visitorHash, :createdAt)
        ON CONFLICT (email) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("email") String email,
                       @Param("visitorHash") String visitorHash,
                       @Param("createdAt") LocalDateTime createdAt);
}
