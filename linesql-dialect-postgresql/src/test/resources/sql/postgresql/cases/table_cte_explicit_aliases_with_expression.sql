WITH q(kind, payload) AS (SELECT e.event_time + e.time_delta, e.event_time FROM public.events e)
TABLE q ORDER BY 1, 2;
