SELECT u.id, o.amount
FROM (SELECT id, name FROM ods.users) u
JOIN (SELECT id, amount FROM dwd.orders) o USING (id);
