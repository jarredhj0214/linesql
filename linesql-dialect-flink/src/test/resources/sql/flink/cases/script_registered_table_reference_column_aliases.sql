CREATE TABLE ods.events (id BIGINT, observed_at TIMESTAMP(3))
WITH ('connector' = 'datagen');
SELECT e.event_id, e.recorded_at
FROM ods.events AS e(event_id, recorded_at) ORDER BY 2;
SELECT * FROM ods.events ORDER BY 2;
