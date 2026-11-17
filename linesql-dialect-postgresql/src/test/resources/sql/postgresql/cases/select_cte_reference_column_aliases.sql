WITH entries AS (SELECT e.id, e.observed_at FROM public.events e)
SELECT a.event_id, a.observed_at, b.other_id
FROM entries AS a(event_id) JOIN entries AS b(other_id) ON a.event_id = b.other_id
ORDER BY a.event_id;
