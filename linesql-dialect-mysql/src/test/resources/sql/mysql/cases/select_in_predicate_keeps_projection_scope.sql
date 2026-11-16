SELECT a.id,
    a.id IN (SELECT f.account_id FROM archive.orders f WHERE f.valid = 1) AS has_active_order
FROM live.accounts a;
