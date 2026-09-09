SELECT o.user_id, o.amount
FROM dwd.orders o
SORT BY o.amount DESC;
