WITH entries AS (SELECT e.id, e.observed_at FROM ods.events e)
SELECT a.event_id, a.observed_at, b.change_id
FROM entries AS a(event_id, observed_at)
JOIN entries AS b(change_id, recorded_at) ON a.event_id = b.change_id
ORDER BY 1;
