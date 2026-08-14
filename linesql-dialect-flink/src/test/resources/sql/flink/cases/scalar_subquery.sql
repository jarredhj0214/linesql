SELECT
    u.id,
    u.name,
    (SELECT count(*) FROM ods.orders o WHERE o.user_id = u.id) AS order_count
FROM ods.users u;
