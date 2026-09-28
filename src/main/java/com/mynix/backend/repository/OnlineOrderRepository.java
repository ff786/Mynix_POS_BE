package com.mynix.backend.repository;

import com.mynix.backend.model.OnlineOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnlineOrderRepository extends JpaRepository<OnlineOrder, Long> {

    Optional<OnlineOrder> findByRequestId(UUID requestId);

    Optional<OnlineOrder> findByInvoiceNumber(String invoiceNumber);

    Optional<OnlineOrder> findBySale_PublicInvoiceToken(String publicInvoiceToken);

    boolean existsByPaymentReference(String paymentReference);

    List<OnlineOrder> findTop50BySale_Customer_IdOrderByCreatedAtDesc(Long customerId);

    List<OnlineOrder> findTop300ByOrderByCreatedAtDesc();
}
