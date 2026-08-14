package com.mynix.backend.util;

import com.mynix.backend.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private final SaleRepository saleRepository;

    public String generate() {

        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        long countToday = saleRepository.count() + 1;

        return String.format("INV-%s-%04d", date, countToday);
    }
}