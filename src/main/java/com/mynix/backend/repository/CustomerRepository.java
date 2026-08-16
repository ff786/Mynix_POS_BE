package com.mynix.backend.repository;

import com.mynix.backend.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository
        extends JpaRepository<Customer, Long> {

    List<Customer> findByActiveTrueOrderByNameAsc();

    List<Customer> findByNameContainingIgnoreCaseOrContactNumberContaining(
            String name,
            String contactNumber
    );

    Optional<Customer> findByContactNumber(String contactNumber);

    boolean existsByContactNumber(String contactNumber);

    boolean existsByContactNumberAndIdNot(
            String contactNumber,
            Long id
    );
}