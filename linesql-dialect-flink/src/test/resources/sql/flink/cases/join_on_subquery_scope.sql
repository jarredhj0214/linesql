SELECT u.id, o.amount
FROM (SELECT id, name FROM ods_users) u
JOIN (SELECT user_id, amount FROM dwd_orders) o ON u.id = o.user_id;
