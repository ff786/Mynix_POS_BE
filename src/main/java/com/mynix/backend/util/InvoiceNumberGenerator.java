package com.mynix.backend.util;

import com.mynix.backend.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private final SaleRepository saleRepository;

    public String generate() {

        LocalDate today = LocalDate.now();

        LocalDateTime start = today.atStartOfDay();

        LocalDateTime end = today.plusDays(1).atStartOfDay();

        long todayCount = saleRepository.countByCreatedAtBetween(start, end) + 1;

        String date = today.format(DateTimeFormatter.BASIC_ISO_DATE);

        return String.format("INV-%s-%04d", date, todayCount);
    }
}