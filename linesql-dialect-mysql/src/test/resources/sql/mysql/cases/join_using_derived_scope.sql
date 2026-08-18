WITH user_base AS (
  SELECT id, name FROM app.users
), order_base AS (
  SELECT id, amount FROM app.orders
)
SELECT user_base.id, order_base.amount
FROM user_base
JOIN order_base USING (id);
