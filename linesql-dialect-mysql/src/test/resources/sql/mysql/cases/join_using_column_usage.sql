SELECT u.id, o.amount
FROM app.users u
JOIN app.orders o USING (id);
