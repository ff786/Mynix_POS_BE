package com.mynix.backend.dto.checkout;

import com.mynix.backend.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CheckoutRequest {

    @Valid
    @NotEmpty
    private List<CheckoutItem> items;

    @NotNull
    private PaymentMethod paymentMethod;
}