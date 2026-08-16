ALTER TABLE sales
    ADD COLUMN customer_id BIGINT;

ALTER TABLE sales
    ADD CONSTRAINT fk_sales_customer
        FOREIGN KEY (customer_id)
            REFERENCES customers(id);

CREATE INDEX idx_sales_customer
    ON sales(customer_id);