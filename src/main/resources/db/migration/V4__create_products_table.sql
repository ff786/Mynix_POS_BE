CREATE TABLE products
(
    id              BIGSERIAL PRIMARY KEY,

    name            VARCHAR(150) NOT NULL,

    barcode         VARCHAR(50) NOT NULL UNIQUE,

    category_id     BIGINT NOT NULL,

    buying_price    NUMERIC(12,2) NOT NULL,

    selling_price   NUMERIC(12,2) NOT NULL,

    stock_quantity  INTEGER NOT NULL DEFAULT 0,

    minimum_stock   INTEGER NOT NULL DEFAULT 5,

    image_url       VARCHAR(500),

    active          BOOLEAN NOT NULL DEFAULT TRUE,

    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_products_category
        FOREIGN KEY (category_id)
            REFERENCES categories(id)
);

CREATE INDEX idx_products_name
    ON products(name);

CREATE INDEX idx_products_barcode
    ON products(barcode);

CREATE INDEX idx_products_category
    ON products(category_id);