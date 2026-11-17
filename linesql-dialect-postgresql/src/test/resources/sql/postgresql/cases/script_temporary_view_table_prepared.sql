SET search_path TO public;
CREATE TEMP VIEW recent (event_key, observed_at) AS
SELECT e.id, e.observed_at FROM ods.events e WHERE e.active;
TABLE recent ORDER BY 2;
PREPARE read_recent AS SELECT event_key FROM recent;
EXECUTE read_recent;
EXPLAIN EXECUTE read_recent;
DISCARD TEMP;
TABLE recent;
