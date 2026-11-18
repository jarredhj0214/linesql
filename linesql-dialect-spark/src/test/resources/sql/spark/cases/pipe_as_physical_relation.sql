FROM ods.events
|> AS incoming
|> WHERE incoming.event_type = 'click'
|> SELECT incoming.id AS event_id
