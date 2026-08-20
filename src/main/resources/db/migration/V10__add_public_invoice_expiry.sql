ALTER TABLE sales
    ADD COLUMN IF NOT EXISTS public_invoice_expires_at TIMESTAMP;