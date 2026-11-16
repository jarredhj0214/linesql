INSERT INTO mart.audit (order_id, max_flag)
SELECT o.id, (
    SELECT MAX(f.flag) FROM ods.flags AS f
    WHERE f.order_id = o.id AND o.active = true
)
FROM ods.orders AS o;
