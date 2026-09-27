package com.mynix.backend.util;

import java.util.List;

/** Sri Lankan mobile numbers as used by the online store. */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /** 07XXXXXXXX, 7XXXXXXXX, 947XXXXXXXX or +947XXXXXXXX (spaces/dashes ok) → 07XXXXXXXX; null otherwise. */
    public static String normalizeMobile(String input) {

        if (input == null) {
            return null;
        }
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.length() == 11 && digits.startsWith("947")) {
            digits = "0" + digits.substring(2);
        } else if (digits.length() == 9 && digits.startsWith("7")) {
            digits = "0" + digits;
        }
        return digits.matches("^07[0-9]{8}$") ? digits : null;
    }

    /** Digit-only forms of a number as staff may have typed it in the POS (spaces, "+" ignored). */
    public static List<String> digitVariants(String normalized) {

        String local = normalized.substring(1); // 7XXXXXXXX
        return List.of(normalized, "94" + local, local);
    }
}
