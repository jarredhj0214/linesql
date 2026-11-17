WITH "Live" AS (SELECT id AS event_id FROM ods.live_events),
     live AS (SELECT id AS event_id FROM ods.archived_events)
SELECT "Live".* FROM "Live"
UNION ALL TABLE live
ORDER BY 1;
