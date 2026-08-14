SELECT
    user_id,
    amount,
    row_number() OVER (PARTITION BY user_id ORDER BY created_at DESC) AS rn,
    sum(amount) OVER (PARTITION BY user_id) AS total_amount
FROM ods.orders;
