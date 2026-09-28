-- Delivery addresses customers save in their website account.
CREATE TABLE customer_addresses
(
    id            BIGSERIAL PRIMARY KEY,
    customer_id   BIGINT       NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    label         VARCHAR(40)  NOT NULL,
    address_line1 VARCHAR(200) NOT NULL,
    address_line2 VARCHAR(200),
    city          VARCHAR(100) NOT NULL,
    district      VARCHAR(100) NOT NULL,
    postal_code   VARCHAR(20),
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP
);

CREATE INDEX idx_customer_addresses_customer
    ON customer_addresses (customer_id);

-- At most one default address per customer.
CREATE UNIQUE INDEX uq_customer_addresses_default
    ON customer_addresses (customer_id) WHERE is_default;
