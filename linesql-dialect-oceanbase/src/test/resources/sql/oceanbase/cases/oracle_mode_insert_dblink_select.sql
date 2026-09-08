INSERT INTO ods.remote_orders@remote_dw (id, status)
SELECT id, status
FROM ods.orders
WHERE dt = '2026-01-01';
