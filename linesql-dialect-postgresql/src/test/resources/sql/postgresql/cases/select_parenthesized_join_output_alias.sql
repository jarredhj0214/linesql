SELECT joined.id AS event_id, id AS merged_id
FROM (public.events a FULL JOIN public.legacy_events b USING (id)) AS joined
WHERE joined.id > 0;
