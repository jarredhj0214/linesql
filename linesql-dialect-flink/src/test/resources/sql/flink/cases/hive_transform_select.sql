SELECT TRANSFORM (o.user_id, o.amount)
USING 'python normalize_order.py'
AS norm_user, norm_amount
FROM dwd.orders o
