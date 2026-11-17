SELECT q.* FROM (SELECT 1, e.id FROM ods.events e) AS q(batch_id, event_id)
ORDER BY 2;
