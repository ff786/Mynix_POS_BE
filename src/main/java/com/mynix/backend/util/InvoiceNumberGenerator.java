package com.mynix.backend.util;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Next invoice number for today. The per-day counter row is incremented in
     * one statement and stays locked until the sale's transaction ends, so
     * concurrent sales get distinct numbers and a rolled-back sale gives its
     * number back.
     */
    public String generate() {

        LocalDate today = LocalDate.now();

        Integer next = jdbcTemplate.queryForObject("""
                INSERT INTO invoice_counters (day, last_value)
                VALUES (?, 1)
                ON CONFLICT (day)
                DO UPDATE SET last_value = invoice_counters.last_value + 1
                RETURNING last_value
                """, Integer.class, today);

        String date = today.format(DateTimeFormatter.BASIC_ISO_DATE);

        return String.format("INV-%s-%04d", date, next);
    }
}
