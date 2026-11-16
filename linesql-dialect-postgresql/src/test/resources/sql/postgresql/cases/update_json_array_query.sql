UPDATE mart.accounts AS a
SET payments = JSON_ARRAY(
    SELECT p.amount FROM sales.payments AS p WHERE p.account_id = a.id RETURNING jsonb
)
WHERE a.active;
