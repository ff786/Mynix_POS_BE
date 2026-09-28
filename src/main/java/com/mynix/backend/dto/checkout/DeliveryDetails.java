package com.mynix.backend.dto.checkout;

import com.mynix.backend.model.OnlineOrderChannel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * "Deliver this order" in New Sale: an order taken by phone or WhatsApp. Use
 * a saved address (savedAddressId) or type one (optionally saving it).
 */
@Data
public class DeliveryDetails {

    @NotNull
    private OnlineOrderChannel channel;

    private Long savedAddressId;

    @Size(max = 40)
    private String label;

    @Size(max = 200)
    private String addressLine1;

    @Size(max = 200)
    private String addressLine2;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String district;

    @Size(max = 20)
    private String postalCode;

    private boolean saveAddress;

    @Size(max = 500)
    private String notes;
}
