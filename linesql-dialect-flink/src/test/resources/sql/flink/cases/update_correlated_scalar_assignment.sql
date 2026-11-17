UPDATE mart.orders
SET latest_flag = COALESCE((
    SELECT MAX(f.change_flag)
    FROM ods.flags AS f
    WHERE f.order_id = orders.order_id
), current_flag)
WHERE keep_flag;
