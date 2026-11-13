WITH ranked AS (
    SELECT e.id, ROW_NUMBER() OVER (PARTITION BY e.tenant_id ORDER BY e.created_at) AS seq
    FROM ods.events AS e
)
INSERT INTO mart.ranked_events (event_id, seq)
SELECT r.id, r.seq FROM ranked AS r;
