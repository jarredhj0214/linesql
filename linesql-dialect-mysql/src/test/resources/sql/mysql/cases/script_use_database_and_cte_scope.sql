USE `ods`;
CREATE TEMPORARY TABLE stage AS SELECT id, status FROM orders WHERE active = 1;
USE mart;
INSERT INTO summary (id) SELECT id FROM ods.stage WHERE status = 'ready';
WITH stage AS (SELECT id FROM live) SELECT id FROM stage;
SELECT id FROM stage;
SELECT 1 FROM DUAL;
PREPARE read_orders FROM 'SELECT id FROM orders WHERE id > ?';
