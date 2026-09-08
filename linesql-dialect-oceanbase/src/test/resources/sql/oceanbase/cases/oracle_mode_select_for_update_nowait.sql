SELECT id, status
FROM ods.orders
WHERE status = 'PENDING'
FOR UPDATE OF status NOWAIT;
