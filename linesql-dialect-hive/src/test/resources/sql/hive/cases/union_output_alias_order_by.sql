SELECT a.id AS event_id FROM ods.events AS a
UNION ALL
SELECT b.id AS archived_id FROM ods.events_archive AS b
ORDER BY event_id;
