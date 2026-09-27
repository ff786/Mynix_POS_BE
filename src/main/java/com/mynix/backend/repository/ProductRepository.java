package com.mynix.backend.repository;

import com.mynix.backend.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByBarcode(String barcode);
    List<Product> findByActiveTrue();
    List<Product> findByNameContainingIgnoreCaseAndActiveTrue(String name);

    boolean existsByBarcode(String barcode);

    boolean existsByNameIgnoreCase(String name);

    @Query("""
        SELECT COUNT(p)
        FROM Product p
        WHERE p.active = true
        AND p.stockQuantity <= p.minimumStock
        """)
            long countLowStockProducts();

    /**
     * Takes stock in a single statement, only if enough is left. Returns the
     * number of rows changed: 0 means insufficient stock. Safe when the till
     * and the website sell the same item at the same moment.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE Product p
        SET p.stockQuantity = p.stockQuantity - :quantity
        WHERE p.id = :id
        AND p.stockQuantity >= :quantity
        """)
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);
}