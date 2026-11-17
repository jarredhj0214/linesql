WITH ranked AS (
  SELECT e.id, ROW_NUMBER() OVER (PARTITION BY e.user_id ORDER BY e.event_time) AS rn
  FROM ods.events e
)
SELECT id FROM ranked WHERE rn = 1;
