SELECT u.id, o.amount
FROM (SELECT id, name FROM app.users) u
JOIN (SELECT id, amount FROM app.orders) o USING (id);
