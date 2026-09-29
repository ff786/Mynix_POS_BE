package com.mynix.backend.service;

import com.mynix.backend.dto.variant.VariantGroupRequest;
import com.mynix.backend.dto.variant.VariantGroupResponse;
import com.mynix.backend.model.ProductVariantGroup;
import com.mynix.backend.repository.ProductRepository;
import com.mynix.backend.repository.ProductVariantGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Variant groups: products sold as options of one website listing. */
@Service
@RequiredArgsConstructor
public class VariantGroupService {

    private static final String DEFAULT_OPTION_NAME = "Option";

    private final ProductVariantGroupRepository groupRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<VariantGroupResponse> getAll() {
        return groupRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public VariantGroupResponse create(VariantGroupRequest request) {
        ProductVariantGroup group = ProductVariantGroup.builder()
                .name(request.getName().trim())
                .optionName(optionName(request))
                .build();
        return toResponse(groupRepository.save(group));
    }

    @Transactional
    public VariantGroupResponse update(Long id, VariantGroupRequest request) {
        ProductVariantGroup group = find(id);
        group.setName(request.getName().trim());
        group.setOptionName(optionName(request));
        return toResponse(groupRepository.save(group));
    }

    /** Products in the group become standalone again (the column is ON DELETE SET NULL). */
    @Transactional
    public void delete(Long id) {
        groupRepository.delete(find(id));
    }

    public ProductVariantGroup find(Long id) {
        return groupRepository.findById(id).orElseThrow(() -> new RuntimeException("Variant group not found."));
    }

    private static String optionName(VariantGroupRequest request) {
        String name = request.getOptionName();
        return name == null || name.isBlank() ? DEFAULT_OPTION_NAME : name.trim();
    }

    private VariantGroupResponse toResponse(ProductVariantGroup group) {
        return VariantGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .optionName(group.getOptionName())
                .products(productRepository.findByVariantGroupIdAndActiveTrueOrderByVariantLabelAsc(group.getId())
                        .stream()
                        .map(p -> VariantGroupResponse.Member.builder()
                                .id(p.getId())
                                .name(p.getName())
                                .variantLabel(p.getVariantLabel())
                                .build())
                        .toList())
                .build();
    }
}
