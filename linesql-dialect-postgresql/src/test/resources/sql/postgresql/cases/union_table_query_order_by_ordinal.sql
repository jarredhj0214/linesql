WITH public_rows AS (SELECT id, event_time FROM public.events),
     archive_rows AS (SELECT id, event_time FROM archive.events)
(TABLE public_rows UNION ALL TABLE archive_rows) ORDER BY 2;
