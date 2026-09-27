-- Website orders. Each one is a normal POS sale (same stock, invoice numbering
-- and reports) plus the details the POS has no place for: delivery address
-- and order status.
CREATE TABLE online_orders
(
    id                BIGSERIAL PRIMARY KEY,

    -- The POS sale. If staff delete the sale in the POS (e.g. a refused COD
    -- order) the link is cleared and the order is treated as cancelled;
    -- deleting sales works exactly as before.
    sale_id           BIGINT UNIQUE REFERENCES sales (id) ON DELETE SET NULL,
    invoice_number    VARCHAR(30)  NOT NULL UNIQUE,

    -- Sent by the website with every order, so a retried request can never
    -- create a second sale.
    request_id        UUID         NOT NULL UNIQUE,

    payment_method    VARCHAR(30)  NOT NULL,
    -- OnePay transaction id for card orders; unique so one payment can't be
    -- recorded twice.
    payment_reference VARCHAR(100) UNIQUE,

    status            VARCHAR(30)  NOT NULL,

    customer_name     VARCHAR(150) NOT NULL,
    customer_phone    VARCHAR(30)  NOT NULL,
    customer_email    VARCHAR(254),

    address_line1     VARCHAR(200) NOT NULL,
    address_line2     VARCHAR(200),
    city              VARCHAR(100) NOT NULL,
    district          VARCHAR(100) NOT NULL,
    postal_code       VARCHAR(20),
    delivery_notes    VARCHAR(500),

    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP
);

CREATE INDEX idx_online_orders_status
    ON online_orders (status);

CREATE INDEX idx_online_orders_phone
    ON online_orders (customer_phone);
