package com.mynix.backend.storage;

import java.io.InputStream;

/** Where uploaded product photos and videos live (Cloudflare R2 in production). */
public interface MediaStorage {

    /** False until the storage settings are filled in; uploads are refused meanwhile. */
    boolean isConfigured();

    /** Stores a file. The content can be opened more than once (retries, checksums). */
    void put(String key, Content content, long size, String contentType);

    /** Opens the upload's bytes; each call starts from the beginning. */
    @FunctionalInterface
    interface Content {
        InputStream open() throws java.io.IOException;
    }

    void delete(String key);

    /** The public address of a stored file. */
    String publicUrl(String key);
}
