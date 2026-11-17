WITH "snapshot.data" (event_key) AS MATERIALIZED (
    SELECT e.id FROM ods.events AS e
)
SELECT s.event_key
FROM "snapshot.data" AS s
WHERE s.event_key > 0;
