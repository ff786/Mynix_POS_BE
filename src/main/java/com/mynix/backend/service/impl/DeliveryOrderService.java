package com.mynix.backend.service.impl;

import com.mynix.backend.dto.checkout.DeliveryDetails;
import com.mynix.backend.dto.store.StoreAddressRequest;
import com.mynix.backend.model.Customer;
import com.mynix.backend.model.CustomerAddress;
import com.mynix.backend.model.OnlineOrder;
import com.mynix.backend.model.OnlineOrderChannel;
import com.mynix.backend.model.OnlineOrderStatus;
import com.mynix.backend.model.OnlinePaymentMethod;
import com.mynix.backend.model.PaymentMethod;
import com.mynix.backend.model.Sale;
import com.mynix.backend.repository.OnlineOrderRepository;
import com.mynix.backend.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Orders staff take by phone or WhatsApp in New Sale ("Deliver this order"):
 * the sale also gets a delivery record, so it shows in Online Orders to pack,
 * dispatch and deliver, with the same SMS as website orders.
 */
@Service
@RequiredArgsConstructor
public class DeliveryOrderService {

    private final OnlineOrderRepository onlineOrderRepository;
    private final CustomerProfileService profileService;
    private final OnlineOrderSmsService orderSms;

    /** Checks the delivery details before the sale is created. */
    public void validate(DeliveryDetails delivery, Customer customer, PaymentMethod paymentMethod) {

        if (customer == null) {
            throw new RuntimeException("Choose the customer for a delivery order.");
        }
        if (delivery.getChannel() == OnlineOrderChannel.WEBSITE) {
            throw new RuntimeException("Choose where the order came from: Phone or WhatsApp.");
        }
        if (paymentMethod != PaymentMethod.CREDIT && paymentMethod != PaymentMethod.BANK_DEPOSIT) {
            throw new RuntimeException(
                    "Delivery orders are paid by cash on delivery (Credit) or bank transfer (Bank Deposit).");
        }
        if (delivery.getSavedAddressId() != null) {
            profileService.addressOf(customer.getId(), delivery.getSavedAddressId());
        } else if (isBlank(delivery.getAddressLine1()) || isBlank(delivery.getCity()) || isBlank(delivery.getDistrict())) {
            throw new RuntimeException("Enter the delivery address, city and district.");
        }
    }

    /** Creates the delivery record for a completed sale and texts the customer. */
    public OnlineOrder create(Sale sale, Customer customer, DeliveryDetails delivery, PaymentMethod paymentMethod) {

        Address address = resolveAddress(customer, delivery);
        String phone = PhoneNumbers.normalizeMobile(customer.getContactNumber());

        OnlineOrder order = onlineOrderRepository.save(OnlineOrder.builder()
                .sale(sale)
                .invoiceNumber(sale.getInvoiceNumber())
                .requestId(UUID.randomUUID())
                .channel(delivery.getChannel())
                .paymentMethod(paymentMethod == PaymentMethod.BANK_DEPOSIT
                        ? OnlinePaymentMethod.BANK_TRANSFER
                        : OnlinePaymentMethod.CASH_ON_DELIVERY)
                .status(OnlineOrderStatus.PLACED)
                .customerName(customer.getName())
                .customerPhone(phone != null ? phone : customer.getContactNumber())
                .customerEmail(customer.getEmail())
                .addressLine1(address.line1())
                .addressLine2(address.line2())
                .city(address.city())
                .district(address.district())
                .postalCode(address.postalCode())
                .deliveryNotes(blankToNull(delivery.getNotes()))
                .statusUpdatedAt(LocalDateTime.now())
                .statusUpdatedBy(sale.getCreatedBy())
                .build());

        if (phone != null) {
            orderSms.orderPlaced(order, sale.getPublicInvoiceToken(), sale.getGrandTotal());
        }
        return order;
    }

    private record Address(String line1, String line2, String city, String district, String postalCode) {}

    private Address resolveAddress(Customer customer, DeliveryDetails delivery) {

        if (delivery.getSavedAddressId() != null) {
            CustomerAddress saved = profileService.addressOf(customer.getId(), delivery.getSavedAddressId());
            return new Address(saved.getAddressLine1(), saved.getAddressLine2(), saved.getCity(),
                    saved.getDistrict(), saved.getPostalCode());
        }

        Address typed = new Address(delivery.getAddressLine1().trim(), blankToNull(delivery.getAddressLine2()),
                delivery.getCity().trim(), delivery.getDistrict().trim(), blankToNull(delivery.getPostalCode()));
        if (delivery.isSaveAddress()) {
            StoreAddressRequest request = new StoreAddressRequest();
            request.setLabel(isBlank(delivery.getLabel()) ? "Delivery" : delivery.getLabel().trim());
            request.setAddressLine1(typed.line1());
            request.setAddressLine2(typed.line2());
            request.setCity(typed.city());
            request.setDistrict(typed.district());
            request.setPostalCode(typed.postalCode());
            try {
                profileService.addFor(customer.getId(), request);
            } catch (RuntimeException fullAddressBook) {
                // The order still goes ahead; the address just isn't kept.
            }
        }
        return typed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
