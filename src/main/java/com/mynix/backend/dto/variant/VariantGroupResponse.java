package com.mynix.backend.dto.variant;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class VariantGroupResponse {

    private Long id;
    private String name;
    private String optionName;
    /** Active products in the group, for the product form. */
    private List<Member> products;

    @Data
    @Builder
    public static class Member {
        private Long id;
        private String name;
        private String variantLabel;
    }
}
