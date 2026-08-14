SELECT
    user_id,
    ts,
    sum(amount) OVER (PARTITION BY user_id ORDER BY ts ROWS BETWEEN 3 PRECEDING AND CURRENT ROW) AS rolling_sum
FROM ods.orders
