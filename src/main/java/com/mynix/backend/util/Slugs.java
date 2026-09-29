package com.mynix.backend.util;

import java.text.Normalizer;
import java.util.Locale;

/** Web addresses from product names: "10x Magnifying Loupe" -> "10x-magnifying-loupe". */
public final class Slugs {

    public static final int MAX_LENGTH = 100;

    private Slugs() {
    }

    public static String of(String text) {
        String slug = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "product" : slug;
    }
}
