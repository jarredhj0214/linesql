SELECT u.id, COUNT(o.id) AS order_count
FROM ods.users u
JOIN dwd.orders o ON u.id = o.user_id
WHERE u.status = 'ACTIVE' AND o.amount > 0
GROUP BY u.id
HAVING COUNT(o.id) > 1
ORDER BY u.id;
