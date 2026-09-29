package com.mynix.backend.repository;

import com.mynix.backend.model.ProductVariantGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductVariantGroupRepository extends JpaRepository<ProductVariantGroup, Long> {

    List<ProductVariantGroup> findAllByOrderByNameAsc();
}
