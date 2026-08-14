package com.mynix.backend.service.impl;

import com.mynix.backend.dto.product.ProductRequest;
import com.mynix.backend.dto.product.ProductResponse;
import com.mynix.backend.model.Category;
import com.mynix.backend.model.Product;
import com.mynix.backend.repository.CategoryRepository;
import com.mynix.backend.repository.ProductRepository;
import com.mynix.backend.service.ProductService;
import com.mynix.backend.util.BarcodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BarcodeGenerator barcodeGenerator;

    @Override
    public ProductResponse create(ProductRequest request) {

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        Product product = Product.builder()
                .name(request.getName().trim())
                .barcode(barcodeGenerator.generate())
                .category(category)
                .buyingPrice(request.getBuyingPrice())
                .sellingPrice(request.getSellingPrice())
                .stockQuantity(request.getStockQuantity())
                .minimumStock(request.getMinimumStock())
                .imageUrl(request.getImageUrl())
                .active(true)
                .build();

        product = productRepository.save(product);

        return map(product);
    }

    @Override
    public List<ProductResponse> getAll() {
        return productRepository.findAll()
                .stream()
                .filter(Product::getActive)
                .map(this::map)
                .toList();
    }

    @Override
    public ProductResponse getById(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        return map(product);
    }

    @Override
    public ProductResponse update(Long id, ProductRequest request) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        product.setName(request.getName().trim());
        product.setCategory(category);
        product.setBuyingPrice(request.getBuyingPrice());
        product.setSellingPrice(request.getSellingPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setMinimumStock(request.getMinimumStock());
        product.setImageUrl(request.getImageUrl());

        product = productRepository.save(product);

        return map(product);
    }

    @Override
    public void delete(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        product.setActive(false);

        productRepository.save(product);
    }

    private ProductResponse map(Product product) {

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .barcode(product.getBarcode())
                .category(product.getCategory().getName())
                .buyingPrice(product.getBuyingPrice())
                .sellingPrice(product.getSellingPrice())
                .stockQuantity(product.getStockQuantity())
                .minimumStock(product.getMinimumStock())
                .imageUrl(product.getImageUrl())
                .active(product.getActive())
                .build();
    }
}