SELECT o.id
FROM ods.orders AS o CROSS JOIN ods.accounts AS a
WHERE EXISTS (
    SELECT 1 FROM ods.flags AS o
    WHERE o.account_id = a.id AND o.active = true
);
