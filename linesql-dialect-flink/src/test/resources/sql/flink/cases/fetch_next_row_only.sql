SELECT id, amount
FROM dwd.orders
ORDER BY order_time
FETCH NEXT ROW ONLY
