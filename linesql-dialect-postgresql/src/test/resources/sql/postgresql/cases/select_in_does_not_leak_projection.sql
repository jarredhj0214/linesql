SELECT o.id
FROM ods.orders AS o
WHERE o.id IN (
    SELECT f.order_id FROM ods.flags AS f WHERE f.active = true
);
