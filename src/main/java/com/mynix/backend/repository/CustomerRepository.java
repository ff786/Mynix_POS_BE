package com.mynix.backend.repository;

import com.mynix.backend.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Customers whose number, ignoring spaces, dashes and "+", is one of the
     * given digit strings (e.g. 0771234567, 94771234567, 771234567).
     */
    @Query(value = """
        SELECT * FROM customers
        WHERE regexp_replace(contact_number, '[^0-9]', '', 'g') IN (:digits)
        ORDER BY active DESC, id
        """, nativeQuery = true)
    List<Customer> findByContactDigits(@Param("digits") List<String> digits);

    boolean existsByContactNumberAndIdNot(
            String contactNumber,
            Long id
    );
}