SELECT id, (
    SELECT MAX(f.flag) AS inner_flag FROM ods.flags f
    WHERE f.order_id = o.id
) AS selected_flag
FROM ods.orders o;
