SELECT o.id, r.sku, r.quantity
FROM ods.orders AS o
CROSS JOIN LATERAL jsonb_to_record(o.payload) AS r(sku text, quantity integer)
WHERE r.quantity > 0;
