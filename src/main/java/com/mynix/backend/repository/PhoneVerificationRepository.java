package com.mynix.backend.repository;

import com.mynix.backend.model.PhoneVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PhoneVerification> findByTokenHash(String tokenHash);
}
