CREATE TABLE lake.ods.events (label STRING, amount INT, id BIGINT) WITH ('connector'='datagen');
ALTER TABLE lake.ods.events MODIFY id BIGINT FIRST;
ALTER TABLE lake.ods.events MODIFY (label STRING AFTER amount, amount BIGINT AFTER id);
ALTER TABLE lake.ods.events RENAME TO event_log;
SELECT * FROM lake.ods.event_log ORDER BY 2;
