PREPARE snapshot(integer) AS
SELECT $1, e.id FROM ods.events AS e WHERE e.kind = $1;
CREATE TABLE mart.events (batch_id, event_id) AS EXECUTE snapshot(1);
EXPLAIN (MEMORY) CREATE TABLE mart.planned (batch_id, event_id) AS EXECUTE snapshot(2);
DEALLOCATE snapshot;
CREATE TABLE mart.unresolved AS EXECUTE snapshot(3);
