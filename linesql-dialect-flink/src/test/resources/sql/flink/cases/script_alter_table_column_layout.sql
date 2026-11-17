CREATE TABLE ods.events (id INT, status STRING) WITH ('connector'='datagen');
ALTER TABLE ods.events ADD sequence_no BIGINT FIRST;
ALTER TABLE ods.events ADD (source_tag STRING AFTER id, region STRING);
ALTER TABLE ods.events DROP (status, region);
ALTER TABLE ods.events RENAME id TO event_id;
SELECT * FROM ods.events ORDER BY 2;
