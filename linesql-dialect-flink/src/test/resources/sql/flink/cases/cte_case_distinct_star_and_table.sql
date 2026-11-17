WITH Live AS (SELECT id AS event_id FROM ods.LiveEvents),
     live AS (SELECT id AS event_id FROM ods.HistoricalEvents)
SELECT Live.* FROM Live
UNION ALL TABLE live
ORDER BY 1;
