WITH staged AS (
    SELECT e.event_id, e.op, e.amount
    FROM ods.raw_events AS e
)
SELECT *
FROM FROM_CHANGELOG(input => TABLE staged);
