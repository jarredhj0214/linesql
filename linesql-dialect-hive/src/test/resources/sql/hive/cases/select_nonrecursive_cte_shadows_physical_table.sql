WITH events AS (
    SELECT event_id AS id, amount
    FROM events
    WHERE active = 1
), filtered AS (
    SELECT id, amount FROM events WHERE amount > 0
)
SELECT id, amount FROM filtered;
