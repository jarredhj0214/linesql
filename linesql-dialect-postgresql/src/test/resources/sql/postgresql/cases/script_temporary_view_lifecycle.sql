SET search_path TO public;
CREATE TEMP VIEW "daily.snapshot" (event_key) AS
SELECT e.event_id FROM ods.events AS e WHERE e.active;
SELECT pg_temp."daily.snapshot".event_key
FROM pg_temp."daily.snapshot";
ALTER VIEW pg_temp."daily.snapshot" RENAME TO current_snapshot;
INSERT INTO mart.summary (event_key) SELECT event_key FROM current_snapshot;
DROP VIEW current_snapshot;
SELECT event_key FROM current_snapshot;
