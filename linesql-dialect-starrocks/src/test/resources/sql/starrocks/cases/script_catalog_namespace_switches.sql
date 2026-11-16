USE iceberg.raw;
SELECT e.id FROM events e;

SET CATALOG lake;
SELECT id FROM stage.events;
USE analytics;

INSERT INTO summary (id, amount)
WITH q AS (SELECT id, amount FROM events)
SELECT id, amount FROM q;

USE CATALOG default_catalog;
USE mart;
SELECT id FROM results;
