INSERT INTO mart.events
TABLE ods.new_events ORDER BY event_id LIMIT 100
ON DUPLICATE KEY UPDATE touches = touches + 1;
