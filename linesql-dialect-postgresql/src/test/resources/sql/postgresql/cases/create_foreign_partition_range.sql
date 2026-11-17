CREATE FOREIGN TABLE IF NOT EXISTS ext.events_2026
PARTITION OF mart.events
FOR VALUES FROM (DATE '2026-01-01') TO (DATE '2027-01-01')
SERVER archive_fdw OPTIONS (schema_name 'history', table_name 'events_2026');
