package com.mynix.backend;

import com.mynix.backend.model.Category;
import com.mynix.backend.model.User;
import com.mynix.backend.model.UserRole;
import com.mynix.backend.repository.CategoryRepository;
import com.mynix.backend.repository.UserRepository;
import com.mynix.backend.storage.MediaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Product photos and videos: uploads, links, YouTube, order, sharing and clean-up. */
@IntegrationTest
class ProductMediaTest {

    private static final String PASSWORD = "test-password-123";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1};
    private static final byte[] MP4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};

    @Autowired Environment environment;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean MediaStorage storage;

    RestClient http;
    String adminToken;
    String cashierToken;
    String storeToken;
    Long categoryId;

    @BeforeEach
    void setUp() {
        when(storage.isConfigured()).thenReturn(true);
        when(storage.publicUrl(anyString())).thenAnswer(inv -> "https://media.example/" + inv.getArgument(0));

        http = RestClient.builder()
                .baseUrl("http://localhost:" + environment.getProperty("local.server.port"))
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        adminToken = login(user("media-admin-" + suffix, UserRole.ADMIN));
        cashierToken = login(user("media-cashier-" + suffix, UserRole.CASHIER));
        storeToken = login(user("media-store-" + suffix, UserRole.ONLINE_STORE));
        categoryId = categoryRepository.save(Category.builder().name("Media " + suffix).build()).getId();
    }

    @Test
    void uploadsAreCheckedByContentAndStored() {
        ResponseEntity<Map> photo = upload("photo.jpg", JPEG, adminToken);
        assertThat(photo.getStatusCode().value()).isEqualTo(200);
        String key = (String) photo.getBody().get("storageKey");
        assertThat(key).matches("products/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.jpg");
        assertThat(photo.getBody()).containsEntry("type", "IMAGE").containsEntry("contentType", "image/jpeg");
        verify(storage).put(eq(key), any(), eq((long) JPEG.length), eq("image/jpeg"));

        assertThat(upload("clip.mp4", MP4, adminToken).getBody()).containsEntry("type", "VIDEO");

        // A renamed text file or an SVG is refused, whatever its name says.
        assertThat(upload("evil.jpg", "<svg onload=alert(1)>....".getBytes(), adminToken).getStatusCode().value()).isEqualTo(400);
        // Cashiers can't upload.
        assertThat(upload("photo.jpg", JPEG, cashierToken).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void uploadsAreRefusedUntilStorageIsSetUp() {
        when(storage.isConfigured()).thenReturn(false);
        ResponseEntity<Map> response = upload("photo.jpg", JPEG, adminToken);
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        verify(storage, never()).put(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void productMediaKeepOrderReachTheWebsiteAndUnusedUploadsAreDeleted() {
        String photoKey = (String) upload("a.jpg", JPEG, adminToken).getBody().get("storageKey");
        String videoKey = (String) upload("b.mp4", MP4, adminToken).getBody().get("storageKey");

        Map<String, Object> form = productForm(List.of(
                Map.of("type", "IMAGE", "url", "https://images.example/tray.jpg", "altText", "Tray from above"),
                Map.of("type", "IMAGE", "storageKey", photoKey, "shared", true),
                Map.of("type", "VIDEO", "storageKey", videoKey),
                Map.of("type", "YOUTUBE", "url", "https://youtu.be/dQw4w9WgXcQ?t=5")));
        Map<?, ?> product = adminSend("POST", "/api/products", form).getBody();

        List<Map<String, Object>> media = (List<Map<String, Object>>) product.get("media");
        assertThat(media).extracting(m -> m.get("type")).containsExactly("IMAGE", "IMAGE", "VIDEO", "YOUTUBE");
        assertThat(media.get(1)).containsEntry("shared", true).containsEntry("url", "https://media.example/" + photoKey);
        assertThat(media.get(3)).containsEntry("youtubeId", "dQw4w9WgXcQ");
        // The first photo is the product's main image (POS screens, labels).
        assertThat(product.get("imageUrl")).isEqualTo("https://images.example/tray.jpg");

        Map<String, Object> listed = ((List<Map<String, Object>>) http.get().uri("/api/store/products")
                .header("Authorization", "Bearer " + storeToken).retrieve().body(List.class))
                .stream().filter(p -> product.get("barcode").equals(p.get("barcode"))).findFirst().orElseThrow();
        assertThat((List<?>) listed.get("media")).hasSize(4);

        // Dropping the uploaded photo deletes it from storage after saving.
        form.put("media", List.of(Map.of("type", "VIDEO", "storageKey", videoKey)));
        Map<?, ?> updated = adminSend("PUT", "/api/products/" + product.get("id"), form).getBody();
        assertThat((List<?>) updated.get("media")).hasSize(1);
        assertThat(updated.get("imageUrl")).isNull();
        verify(storage, timeout(2000)).delete(photoKey);
        verify(storage, never()).delete(videoKey);
    }

    @Test
    void badMediaIsRefused() {
        for (Map<String, Object> item : List.<Map<String, Object>>of(
                Map.of("type", "IMAGE", "url", "http://insecure.example/a.jpg"),
                Map.of("type", "YOUTUBE", "url", "https://vimeo.com/123"),
                Map.of("type", "VIDEO", "url", "https://example.com/video.mp4"),
                Map.of("type", "IMAGE", "storageKey", "../../etc/passwd"),
                Map.of("type", "IMAGE", "storageKey", "products/2026/09/" + UUID.randomUUID() + ".mp4"))) {
            assertThat(adminSend("POST", "/api/products", productForm(List.of(item))).getStatusCode().value())
                    .as(item.toString()).isEqualTo(400);
        }
        verify(storage, never()).delete(startsWith(""));
    }

    private Map<String, Object> productForm(List<Map<String, Object>> media) {
        Map<String, Object> form = new HashMap<>(Map.of("name", "Tray " + UUID.randomUUID().toString().substring(0, 8),
                "categoryId", categoryId, "buyingPrice", 100, "sellingPrice", 200, "stockQuantity", 1, "minimumStock", 1));
        form.put("media", media);
        return form;
    }

    private ResponseEntity<Map> upload(String filename, byte[] content, String token) {
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return http.post().uri("/api/media/uploads").header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Map> adminSend(String method, String path, Object body) {
        return http.method(org.springframework.http.HttpMethod.valueOf(method)).uri(path)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(Map.class);
    }

    private String user(String username, UserRole role) {
        userRepository.save(User.builder().fullName(username).username(username)
                .passwordHash(passwordEncoder.encode(PASSWORD)).role(role).active(true).build());
        return username;
    }

    private String login(String username) {
        Map<?, ?> response = http.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", PASSWORD)).retrieve().body(Map.class);
        return (String) response.get("token");
    }
}
