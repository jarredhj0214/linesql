SELECT o.id, o.amount * ? AS adjusted
FROM dwd.orders AS o
WHERE o.tenant_id = ?
ORDER BY o.created_at
LIMIT ?;
