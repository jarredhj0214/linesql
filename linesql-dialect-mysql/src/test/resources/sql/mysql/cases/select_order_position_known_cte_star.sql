WITH q AS (SELECT e.event_id, e.observed_at FROM ods.events e)
SELECT q.* FROM q ORDER BY 2;
