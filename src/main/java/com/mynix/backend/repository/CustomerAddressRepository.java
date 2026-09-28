package com.mynix.backend.repository;

import com.mynix.backend.model.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByCustomerIdOrderByIsDefaultDescCreatedAtAsc(Long customerId);

    Optional<CustomerAddress> findByIdAndCustomerId(Long id, Long customerId);

    long countByCustomerId(Long customerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CustomerAddress a SET a.isDefault = false WHERE a.customerId = :customerId AND a.isDefault = true")
    void clearDefault(@Param("customerId") Long customerId);
}
