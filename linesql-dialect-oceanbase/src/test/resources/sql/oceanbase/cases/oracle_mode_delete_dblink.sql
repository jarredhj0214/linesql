DELETE FROM ods.remote_orders@remote_dw
WHERE status = 'EXPIRED';
