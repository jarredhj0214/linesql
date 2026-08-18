SELECT u.id, o.amount
FROM (SELECT id, name FROM ods.users) u
JOIN (SELECT user_id, amount FROM dwd.orders) o ON u.id = o.user_id;
