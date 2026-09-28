package com.mynix.backend.service.impl;

import com.mynix.backend.model.OnlineOrder;
import com.mynix.backend.service.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.text.DecimalFormat;

/**
 * SMS for website orders (placed, dispatched, delivered, cancelled). Written
 * for online customers: only this order's amount, never a shop credit
 * balance, and links to the website — never to the POS. Messages go out only
 * after the change is saved, so a failed order never texts the customer.
 */
@Service
@RequiredArgsConstructor
public class OnlineOrderSmsService {

    private static final String SIGN_OFF = "\nInquiries, 0778843815";

    private final SmsService smsService;

    /** The website's public address (MYNIX_WEBSITE_URL), e.g. https://mynix.lk */
    @Value("${mynix.website-url:https://mynix.lk}")
    private String websiteUrl;

    public void orderPlaced(OnlineOrder order, String invoiceToken, BigDecimal total) {
        String payment = switch (order.getPaymentMethod()) {
            case CASH_ON_DELIVERY -> "Total Rs. " + money(total) + ", to pay on delivery. We'll call you to confirm.";
            case CARD -> "Paid by card: Rs. " + money(total) + ".";
            case BANK_TRANSFER -> "Paid by bank transfer: Rs. " + money(total) + ".";
        };
        send(order, "MYNIX: Thank you for your order " + order.getInvoiceNumber() + ". " + payment
                + " View your invoice: " + site() + "/invoice/" + invoiceToken);
    }

    public void dispatched(OnlineOrder order, BigDecimal total) {
        String payment = order.getPaymentMethod().collectsCashOnDelivery()
                ? " Please keep Rs. " + money(total) + " ready for the courier."
                : "";
        send(order, "MYNIX: Your order " + order.getInvoiceNumber() + " is on its way." + payment
                + " Track it: " + site() + "/track");
    }

    public void delivered(OnlineOrder order, BigDecimal total) {
        String payment = order.getPaymentMethod().collectsCashOnDelivery()
                ? " We've received your payment of Rs. " + money(total) + "."
                : "";
        send(order, "MYNIX: Your order " + order.getInvoiceNumber() + " has been delivered." + payment
                + " Thank you for shopping with MYNIX!");
    }

    public void cancelled(OnlineOrder order) {
        String refund = order.getPaymentMethod().collectsCashOnDelivery()
                ? ""
                : " Your payment will be refunded.";
        send(order, "MYNIX: Your order " + order.getInvoiceNumber() + " has been cancelled." + refund
                + " Please contact us if you have any questions.");
    }

    private void send(OnlineOrder order, String message) {
        String phone = order.getCustomerPhone();
        String text = message + SIGN_OFF;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    smsService.sendSms(phone, text);
                }
            });
        } else {
            smsService.sendSms(phone, text);
        }
    }

    private String site() {
        return websiteUrl.trim().replaceAll("/+$", "");
    }

    private static String money(BigDecimal amount) {
        return new DecimalFormat("#,##0.00").format(amount == null ? BigDecimal.ZERO : amount);
    }
}
