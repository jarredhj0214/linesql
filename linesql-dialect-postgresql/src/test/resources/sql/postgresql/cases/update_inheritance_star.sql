UPDATE mart.events * AS e
SET status = s.status
FROM staging.event_updates AS s
WHERE e.id = s.id
RETURNING e.id;
