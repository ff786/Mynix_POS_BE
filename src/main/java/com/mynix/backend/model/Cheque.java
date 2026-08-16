package com.mynix.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cheques")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cheque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "cheque_number", nullable = false, length = 100)
    private String chequeNumber;

    @Column(name = "cheque_date", nullable = false)
    private LocalDate chequeDate;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "deposit_date")
    private LocalDate depositDate;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ChequeStatus status = ChequeStatus.RECEIVED;

    @Column(name = "bounce_reason", length = 255)
    private String bounceReason;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
