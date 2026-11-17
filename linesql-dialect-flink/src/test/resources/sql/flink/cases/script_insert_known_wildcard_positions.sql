CREATE TABLE ods.events (id INT, kind INT) WITH ('connector'='datagen');
INSERT INTO mart.selected (event_id, event_kind, calculated)
SELECT e.*, e.id + e.kind FROM ods.events AS e WHERE e.kind > 0;
