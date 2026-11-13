SELECT a.id AS event_id FROM ods.events a
UNION ALL
SELECT b.id AS archived_id FROM ods.events_archive b
ORDER BY event_id;
