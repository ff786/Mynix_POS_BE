-- One counter per day for invoice numbers (INV-YYYYMMDD-NNNN).
-- Incremented atomically, so concurrent sales (till + website) never get the
-- same number, and deleted sales never cause a number to be reused.
CREATE TABLE invoice_counters
(
    day        DATE PRIMARY KEY,

    last_value INTEGER NOT NULL
);

-- Continue from the highest number already issued on each day.
INSERT INTO invoice_counters (day, last_value)
SELECT to_date(substring(invoice_number FROM 5 FOR 8), 'YYYYMMDD'),
       max(substring(invoice_number FROM 14)::INTEGER)
FROM sales
WHERE invoice_number ~ '^INV-[0-9]{8}-[0-9]+$'
GROUP BY 1;
