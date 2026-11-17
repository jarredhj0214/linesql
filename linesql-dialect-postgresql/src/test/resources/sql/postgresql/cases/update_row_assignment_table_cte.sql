WITH q AS (SELECT s.source_a, s.source_b FROM staging.changes s WHERE s.ready LIMIT 1)
UPDATE public.settings SET (key, value) = (TABLE q) WHERE settings.active;
