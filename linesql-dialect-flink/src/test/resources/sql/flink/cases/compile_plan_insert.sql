COMPILE PLAN '/tmp/order_amount_plan.json' FOR
INSERT INTO ads.order_amounts
SELECT user_id, SUM(amount) AS total_amount
FROM dwd.orders
GROUP BY user_id
