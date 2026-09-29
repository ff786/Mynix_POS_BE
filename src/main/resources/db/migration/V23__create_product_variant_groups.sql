-- Variable products: related POS products (colours, sizes, grits…) grouped
-- so the website shows them as one listing with an option picker. Each
-- variant stays a normal POS product with its own barcode, price and stock.
CREATE TABLE product_variant_groups (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    -- What the options are called on the website: Colour, Size, Grit…
    option_name VARCHAR(40)  NOT NULL DEFAULT 'Option',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE products
    ADD COLUMN variant_group_id BIGINT REFERENCES product_variant_groups (id) ON DELETE SET NULL,
    -- This product's option within its group, e.g. "Black" or "14.5x11.5cm".
    ADD COLUMN variant_label    VARCHAR(60);

CREATE INDEX idx_products_variant_group ON products (variant_group_id);
