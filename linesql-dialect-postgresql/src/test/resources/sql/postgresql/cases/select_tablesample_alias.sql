SELECT e.id
FROM ods.events AS e TABLESAMPLE pg_catalog.system (5)
REPEATABLE (42)
WHERE e.active;
