WITH c(batch_id, event_id) AS (SELECT 1, e.id FROM ods.events e)
SELECT c.event_id, c.batch_id FROM c WHERE c.event_id > 0;
