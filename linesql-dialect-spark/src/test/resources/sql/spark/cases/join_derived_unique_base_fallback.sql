SELECT id, name
FROM ods.users u
JOIN (
  SELECT order_id
  FROM dwd.orders
) o ON u.id = o.order_id
