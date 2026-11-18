WITH stage AS (SELECT id AS event_id FROM ods.events)
FROM stage
|> AS snapshot
|> SELECT snapshot.event_id
