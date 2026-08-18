SELECT u.id, p.channel
FROM app.users u
JOIN app.orders o USING (id)
JOIN app.payments p USING (id);
