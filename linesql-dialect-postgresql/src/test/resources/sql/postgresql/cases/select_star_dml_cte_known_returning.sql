WITH updated AS (
    UPDATE public.settings s SET value = s.value + 1 WHERE s.enabled
    RETURNING s.id, s.value AS current_value
)
SELECT * FROM updated ORDER BY 2;
