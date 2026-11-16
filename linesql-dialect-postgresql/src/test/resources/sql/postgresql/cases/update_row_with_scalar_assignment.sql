UPDATE mart.accounts AS a
SET (balance, status) = (
    (SELECT max(p.amount) FROM sales.payments AS p WHERE p.account_id = a.id),
    a.pending_status
);
