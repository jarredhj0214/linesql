SELECT id, a.payload FROM public.events a FULL JOIN public.legacy_events b USING (id) WHERE id > 0;
