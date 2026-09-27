package com.mynix.backend.repository;

import com.mynix.backend.model.CustomerOtp;
import com.mynix.backend.model.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CustomerOtpRepository extends JpaRepository<CustomerOtp, Long> {

    long countByPhoneAndCreatedAtAfter(String phone, LocalDateTime after);

    Optional<CustomerOtp> findFirstByPhoneAndPurposeOrderByCreatedAtDesc(String phone, VerificationPurpose purpose);

    /** Locked, so two simultaneous guesses can't both use the same attempt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerOtp> findFirstByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String phone, VerificationPurpose purpose);
}
