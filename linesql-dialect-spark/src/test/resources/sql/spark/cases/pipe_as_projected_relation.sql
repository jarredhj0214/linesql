FROM ods.events
|> SELECT id AS event_id, weight
|> AS snapshot
|> SELECT snapshot.event_id AS id
