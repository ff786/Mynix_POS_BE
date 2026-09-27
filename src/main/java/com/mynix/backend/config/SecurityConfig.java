package com.mynix.backend.config;

import com.mynix.backend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration)
            throws Exception {

        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                /*
                 * CORS
                 */
                .cors(Customizer.withDefaults())

                /*
                 * JWT API - no CSRF required
                 */
                .csrf(AbstractHttpConfigurer::disable)

                /*
                 * JWT authentication is stateless
                 */
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                /*
                 * Authorization
                 */
                .authorizeHttpRequests(auth -> auth

                        /*
                         * Allow CORS preflight requests.
                         * This is important for PUT/DELETE.
                         */
                        .requestMatchers(
                                org.springframework.http.HttpMethod.OPTIONS,
                                "/**"
                        ).permitAll()

                        /*
                         * Authentication
                         */
                        .requestMatchers(
                                "/api/auth/**"
                        ).permitAll()

                        /*
                         * Public invoice
                         */
                        .requestMatchers(
                                "/api/public/invoices/**"
                        ).permitAll()

                        /*
                         * Online store: the website's server account only.
                         * Staff don't use it, and it can't reach anything else.
                         */
                        .requestMatchers(
                                "/api/store/**"
                        ).hasRole("ONLINE_STORE")

                        /*
                         * Admin only
                         */
                        .requestMatchers(
                                "/api/users/**"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                "/api/categories/**"
                        ).hasRole("ADMIN")

                        /*
                         * POS / Sales / Dashboard
                         */
                        .requestMatchers(
                                "/api/dashboard/**",
                                "/api/products/**",
                                "/api/sales/**",
                                "/api/pos/**"
                        ).hasAnyRole(
                                "ADMIN",
                                "CASHIER"
                        )

                        /*
                         * Everything else: signed-in staff (the only roles
                         * that existed before the online store account).
                         */
                        .anyRequest().hasAnyRole(
                                "ADMIN",
                                "CASHIER"
                        )
                )

                /*
                 * JWT filter
                 */
                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        /*
         * HTTP Basic is intentionally not enabled: every request must use a
         * JWT from /api/auth, so passwords are only ever checked at login.
         */

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        /*
         * Frontend URLs
         */
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "https://mynix-pos-fe.vercel.app"
        ));

        /*
         * All methods required by the POS.
         * PUT and DELETE are explicitly allowed.
         */
        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        /*
         * JWT + JSON headers
         */
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With"
        ));

        /*
         * Frontend is allowed to send credentials.
         */
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }
}