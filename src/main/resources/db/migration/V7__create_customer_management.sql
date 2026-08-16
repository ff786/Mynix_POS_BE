CREATE TABLE customers
(
    id              BIGSERIAL PRIMARY KEY,

    name            VARCHAR(150) NOT NULL,

    contact_number  VARCHAR(30) NOT NULL,

    active          BOOLEAN NOT NULL DEFAULT TRUE,

    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at      TIMESTAMP
);

CREATE INDEX idx_customers_name
    ON customers(name);

CREATE INDEX idx_customers_contact
    ON customers(contact_number);


CREATE TABLE customer_transactions
(
    id              BIGSERIAL PRIMARY KEY,

    customer_id     BIGINT NOT NULL,

    sale_id         BIGINT,

    type            VARCHAR(30) NOT NULL,

    amount          NUMERIC(12,2) NOT NULL,

    description     VARCHAR(255),

    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_customer_transactions_customer
        FOREIGN KEY (customer_id)
            REFERENCES customers(id),

    CONSTRAINT fk_customer_transactions_sale
        FOREIGN KEY (sale_id)
            REFERENCES sales(id)
);

CREATE INDEX idx_customer_transactions_customer
    ON customer_transactions(customer_id);

CREATE INDEX idx_customer_transactions_sale
    ON customer_transactions(sale_id);


CREATE TABLE cheques
(
    id              BIGSERIAL PRIMARY KEY,

    customer_id     BIGINT NOT NULL,

    amount          NUMERIC(12,2) NOT NULL,

    cheque_number   VARCHAR(100) NOT NULL,

    cheque_date     DATE NOT NULL,

    received_date   DATE NOT NULL DEFAULT CURRENT_DATE,

    deposit_date    DATE,

    bank_name       VARCHAR(150),

    status          VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',

    bounce_reason   VARCHAR(255),

    notes           VARCHAR(500),

    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at      TIMESTAMP,

    CONSTRAINT fk_cheques_customer
        FOREIGN KEY (customer_id)
            REFERENCES customers(id)
);

CREATE INDEX idx_cheques_customer
    ON cheques(customer_id);

CREATE INDEX idx_cheques_date
    ON cheques(cheque_date);

CREATE INDEX idx_cheques_status
    ON cheques(status);

CREATE UNIQUE INDEX idx_cheques_customer_number
    ON cheques(customer_id, cheque_number);
