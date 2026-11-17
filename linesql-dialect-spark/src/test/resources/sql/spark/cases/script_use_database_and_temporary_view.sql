USE input_db;
CREATE TEMPORARY VIEW stage AS SELECT s.id FROM daily s WHERE s.active = true;
USE DATABASE report_db;
INSERT INTO summary (id) SELECT id FROM stage;
WITH stage AS (SELECT id FROM live) SELECT id FROM stage;
