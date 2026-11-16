SELECT (
    SELECT f.flag + o.weight FROM ods.flags AS f
    WHERE f.order_id = o.id LIMIT 1
) AS weighted_flag
FROM ods.orders AS o;
