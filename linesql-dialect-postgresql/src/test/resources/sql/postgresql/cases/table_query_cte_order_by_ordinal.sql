WITH q AS (SELECT e.id AS event_id, e.event_time AS observed_at FROM public.events e)
TABLE q ORDER BY 2, event_id;
