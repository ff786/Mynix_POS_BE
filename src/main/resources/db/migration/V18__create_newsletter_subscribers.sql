-- Newsletter sign-ups from the website (moved from Supabase).
CREATE TABLE newsletter_subscribers
(
    id           BIGSERIAL PRIMARY KEY,
    email        VARCHAR(254) NOT NULL UNIQUE,
    -- One-way hash of the visitor's IP, only for rate limiting.
    visitor_hash VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_newsletter_visitor
    ON newsletter_subscribers (visitor_hash, created_at);
