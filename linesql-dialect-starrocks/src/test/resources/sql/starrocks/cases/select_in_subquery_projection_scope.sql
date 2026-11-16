SELECT o.id,
       o.id IN (SELECT f.order_id FROM ods.flags f WHERE f.active = true) AS flagged
FROM ods.orders o;
