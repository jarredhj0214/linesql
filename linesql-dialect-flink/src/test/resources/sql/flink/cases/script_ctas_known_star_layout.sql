CREATE TABLE ods.events (event_id BIGINT, event_time TIMESTAMP(3))
WITH ('connector' = 'datagen');
CREATE TABLE mart.day_events AS SELECT * FROM ods.events;
SELECT * FROM mart.day_events ORDER BY 2;
CREATE TABLE mart.set_events AS
SELECT * FROM ods.events UNION ALL SELECT event_id, event_time FROM ods.backup;
SELECT * FROM mart.set_events ORDER BY 2;
