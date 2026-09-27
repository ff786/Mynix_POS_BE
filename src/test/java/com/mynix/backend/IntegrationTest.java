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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "SPRING_DATASOURCE_PASSWORD=unused-with-testcontainers",
        // Base64, like production (JwtService decodes it). Test-only value.
        "JWT_SECRET=bXluaXgtdGVzdC1vbmx5LWp3dC1zZWNyZXQtbm90LWZvci1wcm9kdWN0aW9uLXVzZS0wMDAwMDA=",
        "TEXTLK_API_TOKEN=test-token",
        "MYNIX_SMS_ENABLED=false"
})
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
