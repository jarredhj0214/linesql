WITH q AS (SELECT s.source_a, s.source_b FROM staging.changes s WHERE s.ready)
UPDATE public.settings SET (key, value) =
    (SELECT * FROM q UNION ALL SELECT b.fallback_a, b.fallback_b FROM staging.backup b LIMIT 1)
WHERE settings.active;
