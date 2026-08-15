package com.mynix.backend.dto.checkout;

import com.mynix.backend.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CheckoutRequest {

    @Valid
    @NotEmpty
    private List<CheckoutItem> items;

    @NotNull
    private PaymentMethod paymentMethod;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal discount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal deliveryFee = BigDecimal.ZERO;
}