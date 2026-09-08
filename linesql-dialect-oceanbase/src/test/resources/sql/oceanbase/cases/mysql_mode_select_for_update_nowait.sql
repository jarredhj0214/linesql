SELECT id, status
FROM mart.orders
WHERE status = 'PENDING'
FOR UPDATE NOWAIT;
