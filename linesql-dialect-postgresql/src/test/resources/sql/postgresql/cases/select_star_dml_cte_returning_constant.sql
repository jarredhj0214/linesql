WITH updated (event_kind, event_time) AS (
    UPDATE public.settings s SET value = s.value + 1
    RETURNING 0, s.observed_at
)
SELECT * FROM updated ORDER BY 2;
