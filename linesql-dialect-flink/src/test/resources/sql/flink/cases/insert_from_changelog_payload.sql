WITH staged AS (
    SELECT e.event_id, e.operation, e.amount
    FROM ods.raw_events AS e
    WHERE e.is_valid
)
INSERT INTO mart.events (event_key, event_amount)
SELECT c.*
FROM FROM_CHANGELOG(input => TABLE staged, op => DESCRIPTOR(operation))
AS c(event_id, amount);
