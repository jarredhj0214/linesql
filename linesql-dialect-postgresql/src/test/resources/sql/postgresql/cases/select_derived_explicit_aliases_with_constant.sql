SELECT q.payload FROM (SELECT 0, e.event_time FROM public.events e) AS q(kind, payload)
ORDER BY 1;
