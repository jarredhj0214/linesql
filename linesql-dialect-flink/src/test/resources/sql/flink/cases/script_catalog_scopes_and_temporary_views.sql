USE CATALOG lake;
USE ods;
CREATE TEMPORARY VIEW stage AS SELECT id, amount FROM orders WHERE status = 'paid';
USE CATALOG lake;
INSERT INTO mart.summary (id, amount)
WITH ready AS (SELECT id, amount FROM stage)
SELECT id, amount FROM ready;
USE warehouse.dm;
SELECT s.id FROM stage s WHERE s.amount > 0;
USE CATALOG reporting;
SELECT e.id FROM logs.events e;
