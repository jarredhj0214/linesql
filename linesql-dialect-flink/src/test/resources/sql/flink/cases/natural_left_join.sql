SELECT u.id, o.amount
FROM ods.users u
NATURAL LEFT JOIN dwd.orders o
