UPDATE mart.orders o
SET (latest_flag, amount) = (
    SELECT 1, f.base_amount + f.tax_amount
    FROM ods.flags f
    WHERE f.order_id = o.order_id
)
WHERE o.keep_flag = 1
RETURNING amount INTO :updated_amount;
