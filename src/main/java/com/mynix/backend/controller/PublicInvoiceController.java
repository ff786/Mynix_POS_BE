package com.mynix.backend.controller;

import com.mynix.backend.dto.publicinvoice.PublicInvoiceResponse;
import com.mynix.backend.service.PublicInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/invoices")
@RequiredArgsConstructor
public class PublicInvoiceController {

    private final PublicInvoiceService publicInvoiceService;

    @GetMapping("/{token}")
    public PublicInvoiceResponse getInvoice(
            @PathVariable String token
    ) {

        return publicInvoiceService.getInvoice(token);
    }
}