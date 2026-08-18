SELECT u.id, o.amount
FROM (SELECT id, name FROM ods_users) u
JOIN (SELECT id, amount FROM dwd_orders) o USING (id);
