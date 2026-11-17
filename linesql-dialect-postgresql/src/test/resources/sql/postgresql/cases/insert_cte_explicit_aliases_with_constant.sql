WITH q(kind, payload) AS (SELECT 0, e.event_time FROM public.events e)
INSERT INTO audit.summary(kind, payload) SELECT * FROM q;
