package com.mynix.backend.model;

/**
 * How a delivery order is paid, and how the POS records it.
 *
 * Cash on delivery is a CREDIT sale, owed until staff mark the order delivered
 * and paid. Card (OnePay) is recorded as CARD once verified. Bank transfer is
 * only for orders staff take by phone/WhatsApp after the transfer arrives
 * (BANK_DEPOSIT); the website offers cash on delivery and card only.
 */
public enum OnlinePaymentMethod {
    CASH_ON_DELIVERY(PaymentMethod.CREDIT),
    CARD(PaymentMethod.CARD),
    BANK_TRANSFER(PaymentMethod.BANK_DEPOSIT);

    private final PaymentMethod posPaymentMethod;

    OnlinePaymentMethod(PaymentMethod posPaymentMethod) {
        this.posPaymentMethod = posPaymentMethod;
    }

    public PaymentMethod posPaymentMethod() {
        return posPaymentMethod;
    }

    /** True when the courier collects the money (the order is still owed). */
    public boolean collectsCashOnDelivery() {
        return this == CASH_ON_DELIVERY;
    }
}
