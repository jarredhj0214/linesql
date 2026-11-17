WITH q(kind, payload) AS (SELECT 0, e.event_time FROM public.events e)
TABLE q ORDER BY 2;
