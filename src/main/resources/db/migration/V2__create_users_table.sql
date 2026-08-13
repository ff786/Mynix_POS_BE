CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,

                       full_name VARCHAR(100) NOT NULL,

                       username VARCHAR(50) NOT NULL UNIQUE,

                       password_hash TEXT NOT NULL,

                       role VARCHAR(20) NOT NULL,

                       active BOOLEAN NOT NULL DEFAULT TRUE,

                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       updated_at TIMESTAMP
);

CREATE INDEX idx_users_username
    ON users(username);