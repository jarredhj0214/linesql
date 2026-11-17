SELECT c1.db.orders.id AS first_id, c2.db.orders.id AS second_id
FROM c1.db.orders
JOIN c2.db.orders ON c1.db.orders.user_id = c2.db.orders.user_id
WHERE c1.db.orders.x > c2.db.orders.y
ORDER BY c2.db.orders.day_id;
