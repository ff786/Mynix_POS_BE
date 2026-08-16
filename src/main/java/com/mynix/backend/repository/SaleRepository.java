package com.mynix.backend.repository;

import com.mynix.backend.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    Optional<Sale> findByInvoiceNumber(String invoiceNumber);
    Optional<Sale> findByPublicInvoiceToken(String publicInvoiceToken);

    @Query("""
    SELECT COALESCE(SUM(s.grandTotal),0)
    FROM Sale s
    WHERE s.createdAt BETWEEN :start AND :end
    """)
    Optional<BigDecimal> sumTodaySales(
        LocalDateTime start,
        LocalDateTime end
    );

    long countByCreatedAtBetween(
        LocalDateTime start,
        LocalDateTime end
    );
}