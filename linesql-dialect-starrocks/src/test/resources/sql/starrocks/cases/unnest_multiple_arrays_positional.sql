SELECT o.id, u.sku, u.quantity
FROM dwd.orders AS o,
     UNNEST(o.skus, o.quantities) AS u(sku, quantity)
WHERE u.quantity > 0;
