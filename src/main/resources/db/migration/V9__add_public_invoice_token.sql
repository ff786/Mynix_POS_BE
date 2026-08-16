ALTER TABLE sales
    ADD COLUMN public_invoice_token VARCHAR(64);

CREATE UNIQUE INDEX uq_sales_public_invoice_token
    ON sales(public_invoice_token);