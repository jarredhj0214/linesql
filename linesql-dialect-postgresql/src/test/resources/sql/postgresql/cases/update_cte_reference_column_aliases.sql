WITH entries AS (SELECT e.id, e.observed_at FROM public.events e)
UPDATE public.summary s SET observed_at = a.recorded_at
FROM entries AS a(event_id, recorded_at)
WHERE s.id = a.event_id;
