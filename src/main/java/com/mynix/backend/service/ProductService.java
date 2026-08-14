package com.mynix.backend.service;

import com.mynix.backend.dto.product.ProductRequest;
import com.mynix.backend.dto.product.ProductResponse;

import java.util.List;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    List<ProductResponse> getAll();

    ProductResponse getById(Long id);

    ProductResponse update(Long id, ProductRequest request);

    void delete(Long id);

}