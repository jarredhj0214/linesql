INSERT INTO mart.account_rollup (batch_id, total, account_id)
SELECT 0, a.amount + a.tax, a.id
FROM ods.accounts AS a
WHERE a.enabled;
