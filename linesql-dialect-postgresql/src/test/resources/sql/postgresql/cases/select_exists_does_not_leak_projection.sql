SELECT o.id
FROM ods.orders AS o
WHERE EXISTS (
    SELECT f.flag FROM ods.flags AS f WHERE f.order_id = o.id
);
