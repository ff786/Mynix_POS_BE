package com.mynix.backend.util;

import com.mynix.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class BarcodeGenerator {

    private static final String PREFIX = "MNX-";
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int LENGTH = 6;

    private final ProductRepository productRepository;
    private final SecureRandom random = new SecureRandom();

    public String generate() {

        String barcode;

        do {
            barcode = PREFIX + randomCode();
        } while (productRepository.existsByBarcode(barcode));

        return barcode;
    }

    private String randomCode() {

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < LENGTH; i++) {
            builder.append(
                    CHARACTERS.charAt(
                            random.nextInt(CHARACTERS.length())
                    )
            );
        }

        return builder.toString();
    }
}