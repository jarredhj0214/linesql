SELECT f.amount
/
NULLIF(f.units, 0) AS ratio
FROM ods.facts f
/
-- The next SQL*Plus buffer contains a DML statement.
INSERT INTO mart.audit (id)
SELECT id FROM ods.events
/
SELECT id FROM mart.audit
/
