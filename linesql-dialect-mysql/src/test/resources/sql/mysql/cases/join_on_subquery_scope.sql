SELECT u.id, o.amount
FROM (SELECT id, name FROM app.users) u
JOIN (SELECT user_id, amount FROM app.orders) o ON u.id = o.user_id;
