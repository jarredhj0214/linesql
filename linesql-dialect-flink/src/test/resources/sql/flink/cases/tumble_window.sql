SELECT
    window_start,
    user_id,
    count(order_id) AS order_count
FROM TABLE(TUMBLE(TABLE ods.orders, DESCRIPTOR(ts), INTERVAL '1' HOUR))
GROUP BY window_start, user_id
