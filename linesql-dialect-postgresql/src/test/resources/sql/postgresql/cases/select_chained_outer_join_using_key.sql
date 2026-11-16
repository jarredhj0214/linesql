SELECT id FROM (public.events a FULL JOIN public.legacy_events b USING (id)) LEFT JOIN public.event_flags f USING (id);
