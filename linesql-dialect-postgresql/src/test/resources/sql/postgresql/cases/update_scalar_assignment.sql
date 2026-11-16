UPDATE mart.accounts AS a
SET balance = COALESCE(
    (SELECT max(p.amount) FROM sales.payments AS p WHERE p.account_id = a.id),
    a.balance
)
WHERE a.active;
