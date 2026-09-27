package com.mynix.backend.model;

/**
 * How a website customer pays. Recorded on the POS sale using the POS's own
 * payment methods: cash on delivery as CASH (with the delivery fee), OnePay
 * as CARD.
 */
public enum OnlinePaymentMethod {
    CASH_ON_DELIVERY(PaymentMethod.CASH),
    CARD(PaymentMethod.CARD);

    private final PaymentMethod posPaymentMethod;

    OnlinePaymentMethod(PaymentMethod posPaymentMethod) {
        this.posPaymentMethod = posPaymentMethod;
    }

    public PaymentMethod posPaymentMethod() {
        return posPaymentMethod;
    }
}
