UPDATE mart.orders o
SET (priority, amount) = (
    SELECT 1, c.base_amount + c.tax_amount
    FROM ods.current_values c
    WHERE c.order_id = o.order_id AND o.use_archive = 0
    UNION ALL
    SELECT 2, a.amount
    FROM ods.archive_values a
    WHERE a.order_id = o.order_id AND o.use_archive = 1
)
WHERE o.keep_flag = 1;
