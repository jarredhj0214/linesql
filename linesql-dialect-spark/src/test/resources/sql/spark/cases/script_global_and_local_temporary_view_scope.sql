USE input_db;
CREATE GLOBAL TEMPORARY VIEW stage AS SELECT s.id FROM orders s WHERE s.status = 'paid';
SELECT stage.id FROM global_temp.stage;
USE report_db;
SELECT id FROM stage;
CREATE TEMPORARY VIEW stage AS SELECT id FROM live;
SELECT id FROM stage;
SELECT g.id FROM global_temp.stage g;
