INSERT INTO TABLE mart.event_archive (event_id, recorded_status)
SELECT v.id, v.status
FROM mart.events FOR SYSTEM_TIME AS OF '2026-10-01 09:30:00' AS v
WHERE v.active;
