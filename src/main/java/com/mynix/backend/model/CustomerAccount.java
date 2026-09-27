package com.mynix.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A website account: an existing POS customer who has verified their mobile number. */
@Entity
@Table(name = "customer_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAccount {

    @Id
    @Column(name = "customer_id")
    private Long customerId;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
