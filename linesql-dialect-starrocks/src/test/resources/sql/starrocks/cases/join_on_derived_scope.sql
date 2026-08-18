WITH user_base AS (
  SELECT id, name FROM ods.users
), order_base AS (
  SELECT user_id, amount FROM dwd.orders
)
SELECT user_base.id, order_base.amount
FROM user_base
JOIN order_base ON user_base.id = order_base.user_id;
