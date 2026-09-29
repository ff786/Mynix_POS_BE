package com.mynix.backend.repository;

import com.mynix.backend.model.ProductMedia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMediaRepository extends JpaRepository<ProductMedia, Long> {

    boolean existsByStorageKey(String storageKey);
}
