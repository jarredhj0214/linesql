WITH l AS (SELECT id, event_time FROM ods.live),
     h AS (SELECT id, event_time FROM ods.history)
SELECT l.* FROM l UNION ALL SELECT h.* FROM h ORDER BY 2;
