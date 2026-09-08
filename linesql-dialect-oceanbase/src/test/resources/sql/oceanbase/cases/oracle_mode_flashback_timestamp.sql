SELECT id, status
FROM ods.orders AS OF TIMESTAMP TO_TIMESTAMP('2026-01-01 00:00:00', 'yyyy-mm-dd hh24:mi:ss')
WHERE status = 'PAID';
