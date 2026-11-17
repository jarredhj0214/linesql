INSERT INTO audit.summary(event_id, recorded_at)
WITH base AS (SELECT e.event_id, e.observed_at FROM ods.events e),
     forwarded(source_id, source_time) AS (SELECT base.* FROM base)
SELECT * FROM forwarded;
