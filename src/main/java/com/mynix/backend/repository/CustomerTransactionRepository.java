package com.mynix.backend.repository;

import com.mynix.backend.model.CustomerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerTransactionRepository
        extends JpaRepository<CustomerTransaction, Long> {

    List<CustomerTransaction>
    findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<CustomerTransaction>
    findByCustomerId(Long customerId);
}