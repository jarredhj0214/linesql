CREATE TABLE ods.events (event_id BIGINT, event_time TIMESTAMP(3))
WITH ('connector' = 'datagen');
CREATE MATERIALIZED TABLE mart.live_events FRESHNESS = INTERVAL '30' SECOND
AS SELECT * FROM ods.events;
ALTER MATERIALIZED TABLE mart.live_events DROP CONSTRAINT event_time;
SELECT * FROM mart.live_events ORDER BY 2;
