-- Several photos and videos per product (each variant is a product), stored
-- in Cloudflare R2 (uploads) or referenced by link (image URL, YouTube).
-- "shared" media shows for every option of a variable product on the website.
CREATE TABLE product_media (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    type        VARCHAR(10)  NOT NULL,             -- IMAGE, VIDEO, YOUTUBE
    storage_key VARCHAR(200),                      -- uploaded file in R2
    url         VARCHAR(1000),                     -- image link (not for uploads)
    youtube_id  VARCHAR(20),
    alt_text    VARCHAR(160),
    shared      BOOLEAN      NOT NULL DEFAULT FALSE,
    position    INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_media_product ON product_media (product_id, position);
CREATE INDEX idx_product_media_storage_key ON product_media (storage_key);

-- Existing photo links become each product's first image.
INSERT INTO product_media (product_id, type, url, alt_text, position)
SELECT id, 'IMAGE', image_url, image_alt, 0
FROM products
WHERE image_url IS NOT NULL AND TRIM(image_url) <> '';
