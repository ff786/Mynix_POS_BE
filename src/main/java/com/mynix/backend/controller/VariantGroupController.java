package com.mynix.backend.controller;

import com.mynix.backend.dto.variant.VariantGroupRequest;
import com.mynix.backend.dto.variant.VariantGroupResponse;
import com.mynix.backend.service.VariantGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Staff can list variant groups; only admins change them (see SecurityConfig). */
@RestController
@RequestMapping("/api/product-variant-groups")
@RequiredArgsConstructor
public class VariantGroupController {

    private final VariantGroupService variantGroupService;

    @GetMapping
    public List<VariantGroupResponse> getAll() {
        return variantGroupService.getAll();
    }

    @PostMapping
    public VariantGroupResponse create(@Valid @RequestBody VariantGroupRequest request) {
        return variantGroupService.create(request);
    }

    @PutMapping("/{id}")
    public VariantGroupResponse update(@PathVariable Long id, @Valid @RequestBody VariantGroupRequest request) {
        return variantGroupService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        variantGroupService.delete(id);
    }
}
