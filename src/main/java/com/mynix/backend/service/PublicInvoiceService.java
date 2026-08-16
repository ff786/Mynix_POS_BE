package com.mynix.backend.service;

import com.mynix.backend.dto.publicinvoice.PublicInvoiceResponse;

public interface PublicInvoiceService {

    PublicInvoiceResponse getInvoice(String token);
}