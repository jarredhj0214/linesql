INSERT INTO audit.merged_keys (event_id, event_day, legacy_id, legacy_day, legacy_payload, body)
SELECT merged.*, b.*, a.payload
FROM public.events a RIGHT JOIN public.legacy_events b USING (id, day) AS merged;
