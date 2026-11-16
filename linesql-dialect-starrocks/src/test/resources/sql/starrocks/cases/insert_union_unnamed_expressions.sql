INSERT INTO mart.order_rollup (amount, status)
SELECT a.amount + 10, a.status FROM ods.current_orders AS a
UNION ALL
SELECT b.total + b.adjustment, 0 FROM ods.archived_orders AS b;
