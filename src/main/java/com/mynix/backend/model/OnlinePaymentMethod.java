package com.mynix.backend.model;

/**
 * How a website customer pays, and how the POS records it.
 *
 * The website offers two: cash on delivery, recorded as a CREDIT sale (owed
 * until staff mark the order delivered and paid, or cancel it), and OnePay,
 * recorded as CARD only after the payment is verified. Bank transfer is a
 * shop-only option.
 */
public enum OnlinePaymentMethod {
    CASH_ON_DELIVERY(PaymentMethod.CREDIT),
    CARD(PaymentMethod.CARD);

    private final PaymentMethod posPaymentMethod;

    OnlinePaymentMethod(PaymentMethod posPaymentMethod) {
        this.posPaymentMethod = posPaymentMethod;
    }

    public PaymentMethod posPaymentMethod() {
        return posPaymentMethod;
    }
}
