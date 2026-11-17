USE source_db;
SELECT e.id FROM events e WHERE e.active = 1
UNION ALL SELECT a.id FROM historical a WHERE a.active = 1;
USE DEFAULT;
INSERT INTO TABLE daily_totals (id) SELECT v.id FROM (SELECT id FROM landing) v;
WITH events AS (SELECT id FROM shared.events) SELECT id FROM events;
