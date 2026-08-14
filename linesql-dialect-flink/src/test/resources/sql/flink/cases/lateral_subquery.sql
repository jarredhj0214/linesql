SELECT o.id, o.user_id, t.max_amount
FROM ods.orders o,
LATERAL (SELECT max(amount) AS max_amount FROM ods.order_items i WHERE i.order_id = o.id) t;
