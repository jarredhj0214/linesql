INSERT INTO audit.merged_keys (event_id, event_day, body)
SELECT merged.*, a.payload
FROM public.events a RIGHT JOIN public.legacy_events b USING (id, day) AS merged;
