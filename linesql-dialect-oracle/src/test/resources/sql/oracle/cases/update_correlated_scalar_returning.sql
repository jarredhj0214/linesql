UPDATE mart.orders o
SET latest_flag = COALESCE((
    SELECT MAX(f.change_flag)
    FROM ods.flags f
    WHERE f.order_id = o.order_id
), o.current_flag)
WHERE o.keep_flag = 1
RETURNING latest_flag INTO :updated_flag;
