package com.mynix.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Full application against the Testcontainers database, with SMS switched off. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "SPRING_DATASOURCE_PASSWORD=unused-with-testcontainers",
        "JWT_SECRET=test-only-secret-test-only-secret-test-only-secret-0123456789",
        "TEXTLK_API_TOKEN=test-token",
        "MYNIX_SMS_ENABLED=false"
})
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
