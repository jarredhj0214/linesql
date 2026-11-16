SELECT o.id
FROM sales.orders o
WHERE o.id IN (
    SELECT i.order_id FROM sales.items i WHERE i.region = o.region
    UNION ALL
    SELECT r.order_id FROM sales.refunds r WHERE r.region = o.region
);
