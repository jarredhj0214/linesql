WITH entries AS (SELECT e.id, e.observed_at FROM ods.events e)
SELECT a.* FROM entries AS a(event_id, recorded_at) ORDER BY 2;
