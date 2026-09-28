-- Where a delivery order came from: the website, or staff taking it by phone
-- or WhatsApp in the POS (New Sale → "Deliver this order").
ALTER TABLE online_orders
    ADD COLUMN channel VARCHAR(20) NOT NULL DEFAULT 'WEBSITE';
