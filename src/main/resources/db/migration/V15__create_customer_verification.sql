-- One-time SMS codes for the online store (checkout and customer sign-in).
-- Codes are stored only as a keyed hash.
CREATE TABLE customer_otps
(
    id          BIGSERIAL PRIMARY KEY,
    phone       VARCHAR(20) NOT NULL,
    purpose     VARCHAR(20) NOT NULL,
    code_hash   VARCHAR(64) NOT NULL,
    attempts    INTEGER     NOT NULL DEFAULT 0,
    expires_at  TIMESTAMP   NOT NULL,
    consumed_at TIMESTAMP,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customer_otps_phone
    ON customer_otps (phone, purpose, created_at);

-- Short-lived, single-use proof that a phone number was verified by SMS code.
-- Only the SHA-256 of the token is stored.
CREATE TABLE phone_verifications
(
    id         BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    phone      VARCHAR(20) NOT NULL,
    purpose    VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- A website account is an existing POS customer (no duplicate customer
-- details): the customers row keeps the name and mobile number. Deleting the
-- customer in the POS removes the account too.
CREATE TABLE customer_accounts
(
    customer_id   BIGINT PRIMARY KEY REFERENCES customers (id) ON DELETE CASCADE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP
);
