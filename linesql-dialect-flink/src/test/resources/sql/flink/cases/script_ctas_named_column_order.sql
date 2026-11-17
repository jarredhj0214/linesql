CREATE TABLE mart.events_reordered (event_time, event_id)
WITH ('connector' = 'kafka')
AS SELECT e.id AS event_id, e.event_time FROM ods.events AS e WHERE e.kind > 0;
SELECT * FROM mart.events_reordered ORDER BY 1;
