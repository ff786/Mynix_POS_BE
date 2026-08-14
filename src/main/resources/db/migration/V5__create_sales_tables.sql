CREATE TABLE sales
(
    id BIGSERIAL PRIMARY KEY,

    invoice_number VARCHAR(30) NOT NULL UNIQUE,

    subtotal NUMERIC(12,2) NOT NULL,

    discount NUMERIC(12,2) NOT NULL DEFAULT 0,

    grand_total NUMERIC(12,2) NOT NULL,

    payment_method VARCHAR(30) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sale_items
(
    id BIGSERIAL PRIMARY KEY,

    sale_id BIGINT NOT NULL,

    product_id BIGINT NOT NULL,

    product_name VARCHAR(150) NOT NULL,

    barcode VARCHAR(50) NOT NULL,

    quantity INTEGER NOT NULL,

    unit_price NUMERIC(12,2) NOT NULL,

    line_total NUMERIC(12,2) NOT NULL,

    CONSTRAINT fk_sale_items_sale
        FOREIGN KEY (sale_id)
            REFERENCES sales(id)
            ON DELETE CASCADE,

    CONSTRAINT fk_sale_items_product
        FOREIGN KEY (product_id)
            REFERENCES products(id)
);

CREATE INDEX idx_sales_invoice
    ON sales(invoice_number);

CREATE INDEX idx_sale_items_sale
    ON sale_items(sale_id);

CREATE INDEX idx_sale_items_product
    ON sale_items(product_id);

CREATE INDEX idx_sale_items_barcode
    ON sale_items(barcode);