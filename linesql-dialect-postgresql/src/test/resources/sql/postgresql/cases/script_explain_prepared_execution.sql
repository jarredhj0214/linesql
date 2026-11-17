PREPARE find_event(text) AS SELECT id FROM ods.events WHERE status = $1;
EXPLAIN (COSTS FALSE) EXECUTE find_event('ready');
DEALLOCATE find_event;
EXPLAIN EXECUTE find_event('ready');
