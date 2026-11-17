SELECT e.id, 'CREATE CATALOG mock; ON CONFLICT DO NOTHING' AS note
FROM ods.events e
WHERE e.id > 0;
