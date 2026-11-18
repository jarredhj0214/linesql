CREATE SCHEDULED QUERY revenue_rollup CRON '0 */10 * * * ? *'
EXECUTED AS 'reports' ENABLED AS
INSERT INTO TABLE mart.daily_revenue (account_id, revenue)
SELECT user_id, SUM(amount) FROM dwd.orders
WHERE order_state = 'paid' GROUP BY user_id
