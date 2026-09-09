SELECT o.user_id, o.amount
FROM dwd.orders o
CLUSTER BY o.user_id;
