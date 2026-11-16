SELECT merged.*, a.payload
FROM public.events a FULL JOIN public.legacy_events b USING (id, day) AS merged
ORDER BY 2;
