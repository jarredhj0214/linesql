SELECT e.event_time + e.time_delta, count(*) AS event_count
FROM public.events e
GROUP BY 1 ORDER BY 1;
