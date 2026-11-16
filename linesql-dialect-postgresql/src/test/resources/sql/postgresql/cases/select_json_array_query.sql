SELECT JSON_ARRAY(
    SELECT a.id + p.amount
    FROM sales.payments AS p
    WHERE p.account_id = a.id
    ORDER BY p.created_at
    RETURNING jsonb
) AS amounts
FROM mart.accounts AS a;
