SELECT joined.event_id, f.flag
FROM (
    public.events a LEFT JOIN public.legacy_events b USING (id, event_day)
) AS joined(event_id, day)
LEFT JOIN public.event_flags f USING (event_id)
WHERE joined.day IS NOT NULL;
