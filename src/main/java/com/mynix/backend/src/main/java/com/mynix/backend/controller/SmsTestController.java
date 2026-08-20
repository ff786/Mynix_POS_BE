package com.mynix.backend.controller;

import com.mynix.backend.service.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test/sms")
@RequiredArgsConstructor
public class SmsTestController {

    private final SmsService smsService;

    @PostMapping
    public String sendTestSms(
            @RequestParam String phone
    ) {

        smsService.sendSms(
                phone,
                """
                MYNIX

                Test SMS successful.

                Your SMS integration is now working.
                
                Helloo Motherfucker

                Thank you for choosing MYNIX.
                """
        );

        return "SMS request sent.";
    }
}