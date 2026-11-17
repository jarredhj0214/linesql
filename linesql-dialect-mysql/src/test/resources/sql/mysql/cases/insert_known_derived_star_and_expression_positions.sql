INSERT INTO audit.summary(event_id, next_id)
SELECT q.*, q.event_id + 1 FROM (SELECT e.event_id FROM ods.events e) AS q;
