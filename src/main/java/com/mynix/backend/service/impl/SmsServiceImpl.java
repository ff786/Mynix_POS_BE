package com.mynix.backend.service.impl;

import com.mynix.backend.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsServiceImpl implements SmsService {

    @Value("${textlk.api-url}")
    private String apiUrl;

    @Value("${textlk.api-token}")
    private String apiToken;

    @Value("${textlk.sender-id}")
    private String senderId;

    private final RestClient restClient;


    @Override
    public void sendSms(
            String phoneNumber,
            String message
    ) {

        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn("SMS skipped: customer has no phone number.");
            return;
        }

        if (message == null || message.isBlank()) {
            log.warn("SMS skipped: message is empty.");
            return;
        }

        String recipient =
                normalizeSriLankanNumber(phoneNumber);

        try {

            Map<String, Object> request =
                    Map.of(
                            "recipient", recipient,
                            "sender_id", senderId,
                            "type", "plain",
                            "message", message
                    );

            log.info(
                    "Sending SMS to {} using sender ID {}",
                    recipient,
                    senderId
            );

            ResponseEntity<String> response =
                    restClient
                            .post()
                            .uri(apiUrl)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + apiToken
                            )
                            .header(
                                    HttpHeaders.CONTENT_TYPE,
                                    MediaType.APPLICATION_JSON_VALUE
                            )
                            .header(
                                    HttpHeaders.ACCEPT,
                                    MediaType.APPLICATION_JSON_VALUE
                            )
                            .body(request)
                            .retrieve()
                            .toEntity(String.class);

            log.info(
                    "Text.lk HTTP Status: {}",
                    response.getStatusCode()
            );

            log.info(
                    "Text.lk Response: {}",
                    response.getBody()
            );

        } catch (Exception e) {

            log.error(
                    "TEXT.LK SMS FAILED for {}",
                    recipient,
                    e
            );

            // TEMPORARY:
            // expose the actual provider failure
            // while we are testing the integration.
            throw new RuntimeException(
                    "Text.lk SMS failed: " + e.getMessage(),
                    e
            );
        }
    }

    private String normalizeSriLankanNumber(
            String phoneNumber
    ) {

        String phone =
                phoneNumber
                        .trim()
                        .replaceAll(
                                "[\\s-()]",
                                ""
                        );

        if (phone.startsWith("+94")) {
            return "94" +
                    phone.substring(3);
        }
        if (phone.startsWith("0094")) {
            return "94" +
                    phone.substring(4);
        }
        if (phone.startsWith("0")) {
            return "94" +
                    phone.substring(1);
        }
        return phone;
    }
}