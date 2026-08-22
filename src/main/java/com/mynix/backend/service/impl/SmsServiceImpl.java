package com.mynix.backend.service.impl;

import com.mynix.backend.model.Customer;
import com.mynix.backend.model.PaymentMethod;
import com.mynix.backend.model.Sale;
import com.mynix.backend.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsServiceImpl implements SmsService {

    private final RestClient restClient;

    @Value("${textlk.api-url}")
    private String apiUrl;

    @Value("${textlk.api-token}")
    private String apiToken;

    @Value("${textlk.sender-id:MYNIX}")
    private String senderId;

    @Value("${mynix.public-invoice-base-url}")
    private String publicInvoiceBaseUrl;

    @Value("${mynix.sms-enabled:true}")
    private boolean smsEnabled;


    @Override
    public void sendSms(
            String recipient,
            String message
    ) {

        if (!smsEnabled) {
            log.info(
                    "MYNIX SMS disabled. Message not sent to {}.",
                    recipient
            );
            return;
        }

        if (recipient == null || recipient.isBlank()) {
            log.warn(
                    "SMS skipped because recipient number is empty."
            );
            return;
        }

        if (message == null || message.isBlank()) {
            log.warn(
                    "SMS skipped because message is empty."
            );
            return;
        }


        String normalizedRecipient =
                normalizeSriLankanNumber(recipient);

        if (normalizedRecipient == null) {
            log.warn(
                    "SMS skipped because phone number could not be normalized: {}",
                    recipient
            );
            return;
        }


        Map<String, Object> payload =
                new LinkedHashMap<>();

        payload.put(
                "recipient",
                normalizedRecipient
        );

        payload.put(
                "sender_id",
                senderId
        );

        payload.put(
                "type",
                "plain"
        );

        payload.put(
                "message",
                message
        );


        try {

            log.info(
                    "Sending MYNIX SMS to {} using sender ID {}",
                    normalizedRecipient,
                    senderId
            );


            Map<String, Object> response =
                    restClient
                            .post()
                            .uri(apiUrl)
                            .header(
                                    "Authorization",
                                    "Bearer " + apiToken
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .accept(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(payload)
                            .retrieve()
                            .body(
                                    new ParameterizedTypeReference<>() {}
                            );


            log.info(
                    "Text.lk response: {}",
                    response
            );


            if (response == null ||
                    response.isEmpty()) {

                log.error(
                        "Text.lk returned an empty response."
                );

                return;
            }


            Object status =
                    response.get("status");


            if ("success".equalsIgnoreCase(
                    String.valueOf(status)
            )) {

                log.info(
                        "MYNIX SMS accepted by Text.lk for {}.",
                        normalizedRecipient
                );

            } else {

                log.error(
                        "Text.lk rejected MYNIX SMS. Response: {}",
                        response
                );
            }


        } catch (Exception e) {

            /*
             * SMS failure must never cancel
             * a successful sale or payment.
             */
            log.error(
                    "MYNIX SMS sending failed for {}.",
                    normalizedRecipient,
                    e
            );
        }
    }


    @Override
    public void sendInvoiceSms(
            Customer customer,
            Sale sale,
            BigDecimal outstanding
    ) {

        if (customer == null || sale == null) {
            return;
        }


        String phone =
                customer.getContactNumber();


        if (phone == null || phone.isBlank()) {

            log.warn(
                    "Invoice SMS skipped for {} because no contact number exists.",
                    customer.getName()
            );

            return;
        }


        BigDecimal safeOutstanding =
                outstanding != null
                        ? outstanding
                        : BigDecimal.ZERO;


        String invoiceUrl =
                buildInvoiceUrl(
                        sale.getPublicInvoiceToken()
                );


        String message =
                "Dear Customer, Your invoice "
                        + sale.getInvoiceNumber()
                        + " total is Rs. "
                        + formatAmount(
                        sale.getGrandTotal()
                )
                        + " and your total outstanding is Rs. "
                        + formatAmount(
                        safeOutstanding
                )
                        + ". Thank you for choosing MYNIX. "
                        + "To view your bill: "
                        + invoiceUrl
                        + "\n"
                        + "Inquiries, 0778843815";


        sendSms(
                phone,
                message
        );
    }


    @Override
    public void sendPaymentSms(
            Customer customer,
            BigDecimal paymentAmount,
            PaymentMethod paymentMethod,
            BigDecimal remainingOutstanding
    ) {

        if (customer == null) {
            return;
        }


        String phone =
                customer.getContactNumber();


        if (phone == null || phone.isBlank()) {

            log.warn(
                    "Payment SMS skipped for {} because no contact number exists.",
                    customer.getName()
            );

            return;
        }


        BigDecimal safePayment =
                paymentAmount != null
                        ? paymentAmount
                        : BigDecimal.ZERO;


        BigDecimal safeOutstanding =
                remainingOutstanding != null
                        ? remainingOutstanding
                        : BigDecimal.ZERO;


        String paymentType =
                formatPaymentMethod(
                        paymentMethod
                );


        String message =
                "Dear Customer, We have received your payment by "
                        + paymentType
                        + " of Rs. "
                        + formatAmount(
                        safePayment
                )
                        + " and your total outstanding is Rs. "
                        + formatAmount(
                        safeOutstanding
                )
                        + "\n"
                        + "Inquiries, 0778843815";


        sendSms(
                phone,
                message
        );
    }


    private String buildInvoiceUrl(
            String token
    ) {

        if (token == null || token.isBlank()) {
            return publicInvoiceBaseUrl;
        }


        String base =
                publicInvoiceBaseUrl
                        .trim()
                        .replaceAll(
                                "/+$",
                                ""
                        );


        return base + "/" + token;
    }


    private String normalizeSriLankanNumber(
            String phone
    ) {

        String value =
                phone
                        .trim()
                        .replaceAll(
                                "[\\s()-]",
                                ""
                        );


        if (value.startsWith("+")) {
            value = value.substring(1);
        }


        if (value.startsWith("0")
                && value.length() == 10) {

            return "94" +
                    value.substring(1);
        }


        if (value.startsWith("94")
                && value.length() >= 11) {

            return value;
        }


        if (value.startsWith("7")
                && value.length() == 9) {

            return "94" + value;
        }


        return null;
    }


    private String formatAmount(
            BigDecimal amount
    ) {

        if (amount == null) {
            return "0.00";
        }

        return amount
                .setScale(
                        2,
                        java.math.RoundingMode.HALF_UP
                )
                .toPlainString();
    }


    private String formatPaymentMethod(
            PaymentMethod method
    ) {

        if (method == null) {
            return "Payment";
        }


        return switch (method) {

            case CASH ->
                    "Cash";

            case CARD ->
                    "Card";

            case BANK_DEPOSIT ->
                    "Bank Deposit";

            case CREDIT ->
                    "Credit";

            case CHEQUE ->
                    "Cheque";
        };
    }
}