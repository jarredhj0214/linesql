WITH entries AS (SELECT e.id, e.observed_at FROM public.events e)
SELECT a.* FROM entries AS a(event_id) ORDER BY 1;
