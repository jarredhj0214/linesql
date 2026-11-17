WITH l AS (SELECT id, event_time FROM ods.live),
     h AS (SELECT id, event_time FROM ods.history)
SELECT 42, l.* FROM l UNION ALL SELECT 0, h.* FROM h ORDER BY 1, 3;
