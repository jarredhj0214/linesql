WITH l AS (SELECT id, event_time FROM ods.live),
     h AS (SELECT id, event_time FROM ods.history)
TABLE l UNION ALL TABLE h ORDER BY 2;
