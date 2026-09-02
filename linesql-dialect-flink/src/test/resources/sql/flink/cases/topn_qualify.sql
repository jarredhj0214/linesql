SELECT user_id, product_id, amount
FROM dwd.order_payments
QUALIFY ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY amount DESC) <= 5
