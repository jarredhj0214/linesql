SELECT u.id, o.amount
FROM ods.users u
JOIN dwd.orders o USING (id);
