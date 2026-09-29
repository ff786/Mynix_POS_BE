package com.mynix.backend.service;

import com.mynix.backend.dto.media.MediaItemRequest;
import com.mynix.backend.dto.media.MediaResponse;
import com.mynix.backend.model.Product;
import com.mynix.backend.model.ProductMedia;
import com.mynix.backend.model.ProductMediaType;
import com.mynix.backend.repository.ProductMediaRepository;
import com.mynix.backend.storage.MediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A product's photos and videos: checks, saves in order, and tidies up storage. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMediaService {

    public static final int MAX_ITEMS = 30;

    private static final Pattern YOUTUBE = Pattern.compile(
            "^https?://(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|embed/|live/)|youtu\\.be/)([A-Za-z0-9_-]{11})(?:[?&#/].*)?$");

    private final MediaStorage storage;
    private final ProductMediaRepository mediaRepository;

    /**
     * Replaces the product's media with the given list (in order) and points
     * the product's main image at the first photo. Uploads no longer used are
     * deleted from storage once the change is saved.
     */
    public void replace(Product product, List<MediaItemRequest> items) {

        if (items.size() > MAX_ITEMS) {
            throw new RuntimeException("A product can have up to " + MAX_ITEMS + " photos and videos.");
        }

        Set<String> before = keysOf(product.getMedia());
        product.getMedia().clear();

        int position = 0;
        for (MediaItemRequest item : items) {
            ProductMedia media = toMedia(item);
            media.setProduct(product);
            media.setPosition(position++);
            product.getMedia().add(media);
        }

        product.setImageUrl(product.getMedia().stream()
                .filter(m -> m.getType() == ProductMediaType.IMAGE)
                .map(this::urlOf)
                .findFirst()
                .orElse(null));

        Set<String> removed = new HashSet<>(before);
        removed.removeAll(keysOf(product.getMedia()));
        deleteAfterCommit(removed);
    }

    public List<MediaResponse> toResponses(Product product) {
        return product.getMedia().stream()
                .map(m -> MediaResponse.builder()
                        .type(m.getType())
                        .storageKey(m.getStorageKey())
                        .url(urlOf(m))
                        .youtubeId(m.getYoutubeId())
                        .altText(m.getAltText())
                        .shared(Boolean.TRUE.equals(m.getShared()))
                        .build())
                .toList();
    }

    /** Where a photo/video can be viewed. */
    public String urlOf(ProductMedia media) {
        if (media.getStorageKey() != null) return storage.publicUrl(media.getStorageKey());
        if (media.getType() == ProductMediaType.YOUTUBE) return "https://www.youtube.com/watch?v=" + media.getYoutubeId();
        return media.getUrl();
    }

    private ProductMedia toMedia(MediaItemRequest item) {

        String key = blankToNull(item.getStorageKey());
        String url = blankToNull(item.getUrl());
        ProductMedia.ProductMediaBuilder media = ProductMedia.builder()
                .type(item.getType())
                .altText(blankToNull(item.getAltText()))
                .shared(item.isShared());

        switch (item.getType()) {
            case YOUTUBE -> {
                Matcher m = url == null ? null : YOUTUBE.matcher(url);
                if (m == null || !m.matches()) {
                    throw new RuntimeException("Paste a YouTube video link, e.g. https://youtu.be/…");
                }
                return media.youtubeId(m.group(1)).build();
            }
            case IMAGE, VIDEO -> {
                if (key != null) {
                    // Only files uploaded through this POS, of the matching kind.
                    if (!MediaUploadService.STORAGE_KEY.matcher(key).matches()
                            || MediaUploadService.typeOfKey(key) != item.getType()) {
                        throw new RuntimeException("That upload isn't valid. Please upload it again.");
                    }
                    return media.storageKey(key).build();
                }
                if (item.getType() == ProductMediaType.VIDEO) {
                    throw new RuntimeException("Upload the video file, or use a YouTube link.");
                }
                if (url == null || !isHttps(url)) {
                    throw new RuntimeException("Photo links must start with https://");
                }
                return media.url(url).build();
            }
            default -> throw new RuntimeException("Unknown media type.");
        }
    }

    private void deleteAfterCommit(Set<String> keys) {
        if (keys.isEmpty()) return;
        Runnable delete = () -> keys.stream()
                .filter(key -> !mediaRepository.existsByStorageKey(key))
                .forEach(key -> {
                    try {
                        storage.delete(key);
                    } catch (RuntimeException e) {
                        log.warn("Could not delete unused media {}: {}", key, e.getMessage());
                    }
                });

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete.run();
                }
            });
        } else {
            delete.run();
        }
    }

    private static Set<String> keysOf(List<ProductMedia> media) {
        Set<String> keys = new HashSet<>();
        media.stream().map(ProductMedia::getStorageKey).filter(Objects::nonNull).forEach(keys::add);
        return keys;
    }

    private static boolean isHttps(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
