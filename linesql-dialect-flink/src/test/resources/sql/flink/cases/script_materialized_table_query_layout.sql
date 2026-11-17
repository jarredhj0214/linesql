CREATE MATERIALIZED TABLE live.metrics FRESHNESS = INTERVAL '30' SECOND
AS SELECT s.key_id, s.event_time FROM input.events s;
SELECT * FROM live.metrics ORDER BY 2;
ALTER MATERIALIZED TABLE live.metrics AS
SELECT s.key_id, s.event_time, s.event_date FROM input.events s;
SELECT * FROM live.metrics ORDER BY 3;
ALTER MATERIALIZED TABLE live.metrics ADD region STRING METADATA VIRTUAL FIRST;
SELECT * FROM live.metrics ORDER BY 1;
ALTER MATERIALIZED TABLE live.metrics MODIFY region STRING METADATA VIRTUAL AFTER event_time;
SELECT * FROM live.metrics ORDER BY 3;
ALTER MATERIALIZED TABLE live.metrics DROP region;
SELECT * FROM live.metrics ORDER BY 3;
DROP MATERIALIZED TABLE live.metrics;
CREATE TABLE live.metrics LIKE external.metrics;
SELECT * FROM live.metrics ORDER BY 2;
