package com.mynix.backend.dto.customer;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CustomerResponse {

    private Long id;

    private String name;

    private String contactNumber;

    private Boolean active;

    private BigDecimal outstanding;
}