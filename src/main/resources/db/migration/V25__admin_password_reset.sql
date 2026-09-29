-- Emergency admin password reset (from the server's settings) and the
-- follow-up forced password change, confirmed by an SMS code.
ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Each reset setting value works once (stored as a SHA-256 hash).
CREATE TABLE admin_password_resets (
    token_hash VARCHAR(64) PRIMARY KEY,
    username   VARCHAR(255) NOT NULL,
    applied_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- One pending SMS code per staff member for changing their password.
CREATE TABLE staff_password_codes (
    user_id    BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    attempts   INTEGER     NOT NULL DEFAULT 0,
    sent_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
