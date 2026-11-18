ALTER SCHEDULED QUERY revenue_rollup DEFINED AS
SELECT user_id AS account_id FROM dwd.orders WHERE amount > 0
