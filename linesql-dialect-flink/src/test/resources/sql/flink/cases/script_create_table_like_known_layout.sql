CREATE TABLE ods.events (
   event_id BIGINT, event_time TIMESTAMP(3), event_day AS CAST(event_time AS DATE)
) WITH ('connector' = 'datagen');
CREATE TABLE mart.copy (region STRING) WITH ('connector' = 'blackhole')
LIKE ods.events (EXCLUDING OPTIONS);
SELECT * FROM mart.copy ORDER BY 3, 4;
CREATE TABLE mart.without_generated LIKE ods.events (EXCLUDING GENERATED);
SELECT * FROM mart.without_generated ORDER BY 2;
