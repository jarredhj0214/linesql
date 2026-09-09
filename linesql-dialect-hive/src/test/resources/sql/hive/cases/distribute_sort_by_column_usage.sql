SELECT o.user_id, o.amount
FROM dwd.orders o
DISTRIBUTE BY o.user_id
SORT BY o.amount DESC;
