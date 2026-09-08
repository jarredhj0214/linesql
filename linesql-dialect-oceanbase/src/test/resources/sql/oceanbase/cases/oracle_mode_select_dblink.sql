SELECT c1 AS remote_id
FROM ods.remote_orders@remote_dw
WHERE status = 'ACTIVE';
