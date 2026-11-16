FROM ods.users AS u JOIN dwd.orders AS o ON u.id = o.user_id
INSERT OVERWRITE TABLE mart.orders_by_user
SELECT u.id AS user_id, sum(o.amount) AS total
WHERE o.status = 'PAID'
GROUP BY u.id
INSERT INTO TABLE mart.active_orders (order_id, customer)
SELECT o.id, u.name WHERE u.active = true;
