package com.mynix.backend.model;

/**
 * How a website customer pays, and how the POS records it.
 *
 * Cash on delivery and bank transfer are CREDIT sales: the customer owes the
 * amount until staff record the payment in the POS (Customers → payment, as
 * Cash or Bank Deposit). A refused delivery is handled by deleting the sale,
 * exactly as for shop sales. OnePay is CARD, recorded only after the payment
 * is verified.
 */
public enum OnlinePaymentMethod {
    CASH_ON_DELIVERY(PaymentMethod.CREDIT),
    BANK_TRANSFER(PaymentMethod.CREDIT),
    CARD(PaymentMethod.CARD);

    private final PaymentMethod posPaymentMethod;

    OnlinePaymentMethod(PaymentMethod posPaymentMethod) {
        this.posPaymentMethod = posPaymentMethod;
    }

    public PaymentMethod posPaymentMethod() {
        return posPaymentMethod;
    }
}
