-- Until now the POS ran on UTC, so every stored date-time was 5 h 30 min
-- behind Sri Lanka time. From this release the POS runs on Sri Lanka time;
-- this one-time step moves existing date-times forward by 5 h 30 min so old
-- and new records line up in lists and reports.
--
-- Only date-time columns (timestamp without time zone) are changed. Date-only
-- fields (cheque dates, invoice counters) and already-issued invoice numbers
-- are left exactly as they are. Flyway's own history is not touched.
DO $$
DECLARE
    col record;
BEGIN
    FOR col IN
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND data_type = 'timestamp without time zone'
          AND table_name <> 'flyway_schema_history'
    LOOP
        EXECUTE format(
            'UPDATE %I SET %I = %I + interval ''5 hours 30 minutes'' WHERE %I IS NOT NULL',
            col.table_name, col.column_name, col.column_name, col.column_name);
    END LOOP;
END $$;
