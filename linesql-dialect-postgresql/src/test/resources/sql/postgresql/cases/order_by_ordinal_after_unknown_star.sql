SELECT e.*, e.event_time + e.time_delta
FROM public.events e ORDER BY 2;
