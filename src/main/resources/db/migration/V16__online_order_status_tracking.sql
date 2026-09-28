-- Who last moved a website order along (packed, dispatched, delivered,
-- cancelled) and when, for the POS "Online Orders" screen.
ALTER TABLE online_orders
    ADD COLUMN status_updated_at TIMESTAMP,
    ADD COLUMN status_updated_by VARCHAR(100);
