SELECT o.id, (
    SELECT f.flag FROM ods.flags AS f
    WHERE f.order_id = o.id LIMIT 1
) AS single_flag
FROM ods.orders AS o;
