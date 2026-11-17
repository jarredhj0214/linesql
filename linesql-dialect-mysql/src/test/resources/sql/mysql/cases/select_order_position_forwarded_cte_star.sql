WITH base AS (SELECT e.event_id, e.observed_at FROM ods.events e),
     forwarded AS (SELECT * FROM base)
SELECT forwarded.* FROM forwarded ORDER BY 2;
