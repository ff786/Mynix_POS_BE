package com.mynix.backend.service;

import com.mynix.backend.dto.media.UploadedMediaResponse;
import com.mynix.backend.model.ProductMediaType;
import com.mynix.backend.storage.MediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores photos and videos uploaded from the POS. The type is taken from the
 * file's first bytes, never from its name or the browser, so only real images
 * (JPEG, PNG, WebP, GIF, AVIF) and videos (MP4, MOV, WebM) get through.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaUploadService {

    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    public static final long MAX_VIDEO_BYTES = 50L * 1024 * 1024;

    /** Keys this service creates; product media may only point at these. */
    public static final Pattern STORAGE_KEY =
            Pattern.compile("^products/\\d{4}/\\d{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp|gif|avif|mp4|mov|webm)$");

    private final MediaStorage storage;

    public UploadedMediaResponse upload(MultipartFile file) {

        if (!storage.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Uploads aren't set up yet. Add the photo by link for now.");
        }
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Choose a photo or video to upload.");
        }

        Detected detected = detect(file);
        if (detected == null) {
            throw new RuntimeException("Only JPEG, PNG, WebP, GIF or AVIF photos and MP4, MOV or WebM videos can be uploaded.");
        }
        long limit = detected.type == ProductMediaType.VIDEO ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
        if (file.getSize() > limit) {
            throw new RuntimeException(detected.type == ProductMediaType.VIDEO
                    ? "Videos can be up to 50 MB. Use a YouTube link for longer videos."
                    : "Photos can be up to 10 MB.");
        }

        YearMonth month = YearMonth.now();
        String key = "products/%d/%02d/%s.%s".formatted(month.getYear(), month.getMonthValue(), UUID.randomUUID(), detected.extension);
        try {
            storage.put(key, file::getInputStream, file.getSize(), detected.contentType);
        } catch (RuntimeException e) {
            log.error("Media upload to storage failed for {}", key, e);
            throw new RuntimeException("The upload failed. Please try again.");
        }

        return UploadedMediaResponse.builder()
                .type(detected.type)
                .storageKey(key)
                .url(storage.publicUrl(key))
                .contentType(detected.contentType)
                .sizeBytes(file.getSize())
                .build();
    }

    /** The media type a storage key holds, from its extension. */
    public static ProductMediaType typeOfKey(String key) {
        return key.matches(".*\\.(mp4|mov|webm)$") ? ProductMediaType.VIDEO : ProductMediaType.IMAGE;
    }

    private record Detected(ProductMediaType type, String contentType, String extension) {
    }

    private static Detected detect(MultipartFile file) {
        byte[] head = new byte[16];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return null;
        }
        if (read < 12) return null;

        if (starts(head, 0xFF, 0xD8, 0xFF)) return new Detected(ProductMediaType.IMAGE, "image/jpeg", "jpg");
        if (starts(head, 0x89, 'P', 'N', 'G')) return new Detected(ProductMediaType.IMAGE, "image/png", "png");
        if (starts(head, 'G', 'I', 'F', '8')) return new Detected(ProductMediaType.IMAGE, "image/gif", "gif");
        if (starts(head, 'R', 'I', 'F', 'F') && ascii(head, 8, 4).equals("WEBP")) {
            return new Detected(ProductMediaType.IMAGE, "image/webp", "webp");
        }
        if (starts(head, 0x1A, 0x45, 0xDF, 0xA3)) return new Detected(ProductMediaType.VIDEO, "video/webm", "webm");
        if (ascii(head, 4, 4).equals("ftyp")) {
            String brand = ascii(head, 8, 4);
            if (brand.equals("avif") || brand.equals("avis")) return new Detected(ProductMediaType.IMAGE, "image/avif", "avif");
            if (brand.equals("qt  ")) return new Detected(ProductMediaType.VIDEO, "video/quicktime", "mov");
            if (Arrays.asList("isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ", "dash", "MSNV")
                    .contains(brand)) {
                return new Detected(ProductMediaType.VIDEO, "video/mp4", "mp4");
            }
        }
        return null;
    }

    private static boolean starts(byte[] data, int... prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }

    private static String ascii(byte[] data, int from, int length) {
        return new String(data, from, length, java.nio.charset.StandardCharsets.US_ASCII);
    }
}
