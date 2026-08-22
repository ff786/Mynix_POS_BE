package com.mynix.backend.service;

import com.mynix.backend.model.Customer;
import com.mynix.backend.model.PaymentMethod;
import com.mynix.backend.model.Sale;

import java.math.BigDecimal;

public interface SmsService {

    void sendSms(
            String recipient,
            String message
    );

    void sendInvoiceSms(
            Customer customer,
            Sale sale,
            BigDecimal outstanding
    );

    void sendPaymentSms(
            Customer customer,
            BigDecimal paymentAmount,
            PaymentMethod paymentMethod,
            BigDecimal remainingOutstanding
    );
}