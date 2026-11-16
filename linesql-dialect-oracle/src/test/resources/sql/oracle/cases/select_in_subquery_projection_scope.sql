SELECT o.id, CASE WHEN o.id IN (
    SELECT f.order_id FROM ods.flags f WHERE f.active = 1
) THEN 1 ELSE 0 END AS flagged
FROM ods.orders o;
