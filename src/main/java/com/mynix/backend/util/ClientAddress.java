package com.mynix.backend.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller's address behind Cloudflare → Nginx: Cloudflare's
 * CF-Connecting-IP, else Nginx's X-Real-IP, else the socket address.
 */
public final class ClientAddress {

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request) {

        for (String header : new String[]{"CF-Connecting-IP", "X-Real-IP"}) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && value.length() <= 45) {
                return value.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
