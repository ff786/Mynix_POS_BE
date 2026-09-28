-- Optional email for customers (given when creating a website account or at
-- checkout). The mobile number stays the key that identifies a customer.
ALTER TABLE customers
    ADD COLUMN email VARCHAR(254);
