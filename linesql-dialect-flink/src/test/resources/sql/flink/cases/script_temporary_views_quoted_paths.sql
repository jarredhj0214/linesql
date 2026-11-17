SET 'execution.runtime-mode' = 'streaming';
CREATE TEMPORARY VIEW `sales.daily`.events AS
SELECT r.event_id FROM ods.raw_events r;
CREATE TEMPORARY VIEW sales.`daily.events` AS
SELECT a.event_id FROM ods.archive_events a;
SELECT recent.event_id AS recent_id, archive_rows.event_id AS archive_id
FROM `sales.daily`.events recent
JOIN sales.`daily.events` archive_rows ON recent.event_id = archive_rows.event_id;
DROP TEMPORARY VIEW `sales.daily`.events;
SELECT a.event_id FROM sales.`daily.events` a;
