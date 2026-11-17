SELECT SUM(e.amount) OVER grouped_ordered AS running_total
FROM ods.events e
WINDOW grouped AS (PARTITION BY e.user_id), grouped_ordered AS (grouped ORDER BY e.event_time);
