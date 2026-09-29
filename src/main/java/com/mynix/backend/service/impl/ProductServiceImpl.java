package com.mynix.backend.service.impl;

import com.mynix.backend.dto.product.ProductRequest;
import com.mynix.backend.dto.product.ProductResponse;
import com.mynix.backend.model.Category;
import com.mynix.backend.model.Product;
import com.mynix.backend.repository.CategoryRepository;
import com.mynix.backend.repository.ProductRepository;
import com.mynix.backend.service.ProductService;
import com.mynix.backend.util.BarcodeGenerator;
import com.mynix.backend.util.Slugs;
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
                .showOnWebsite(request.getShowOnWebsite() == null || request.getShowOnWebsite())
                .barcode(barcodeGenerator.generate())
                .category(category)
                .buyingPrice(request.getBuyingPrice())
                .sellingPrice(request.getSellingPrice())
                .stockQuantity(request.getStockQuantity())
                .minimumStock(request.getMinimumStock())
                .imageUrl(request.getImageUrl())
                .active(true)
                .build();
        applyWebsiteDetails(product, request);

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
        if (request.getShowOnWebsite() != null) {
            product.setShowOnWebsite(request.getShowOnWebsite());
        }
        applyWebsiteDetails(product, request);

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

    /** Full name, description, page address and SEO fields (blank = not set). */
    private void applyWebsiteDetails(Product product, ProductRequest request) {

        String fullName = blankToNull(request.getFullName());
        product.setFullName(fullName == null ? product.getName() : fullName);
        product.setDescription(blankToNull(request.getDescription()));
        product.setSeoTitle(blankToNull(request.getSeoTitle()));
        product.setSeoDescription(blankToNull(request.getSeoDescription()));
        product.setSeoKeywords(blankToNull(request.getSeoKeywords()));
        product.setImageAlt(blankToNull(request.getImageAlt()));

        String requested = blankToNull(request.getSlug());
        if (requested != null) {
            if (isTaken(requested, product.getId())) {
                throw new RuntimeException("The web address \"" + requested + "\" is already used by another product.");
            }
            product.setSlug(requested);
        } else if (product.getSlug() == null) {
            product.setSlug(uniqueSlug(Slugs.of(product.getFullName()), product.getId()));
        }
    }

    private String uniqueSlug(String base, Long productId) {
        String slug = base;
        for (int n = 2; isTaken(slug, productId); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }

    private boolean isTaken(String slug, Long productId) {
        return productId == null
                ? productRepository.existsBySlug(slug)
                : productRepository.existsBySlugAndIdNot(slug, productId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ProductResponse map(Product product) {

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .fullName(product.getFullName())
                .description(product.getDescription())
                .showOnWebsite(product.getShowOnWebsite())
                .slug(product.getSlug())
                .seoTitle(product.getSeoTitle())
                .seoDescription(product.getSeoDescription())
                .seoKeywords(product.getSeoKeywords())
                .imageAlt(product.getImageAlt())
                .barcode(product.getBarcode())
                .categoryId(product.getCategory().getId())
                .category(product.getCategory().getName())
                .buyingPrice(product.getBuyingPrice())
                .sellingPrice(product.getSellingPrice())
                .stockQuantity(product.getStockQuantity())
                .minimumStock(product.getMinimumStock())
                .imageUrl(product.getImageUrl())
                .active(product.getActive())
                .createdAt(product.getCreatedAt())
                .build();
    }
}