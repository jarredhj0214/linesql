INSERT INTO TABLE mart.order_rollup (amount, status)
SELECT a.amount + 10, a.status AS status FROM ods.current_orders a
UNION ALL
SELECT b.total + b.adjustment, 0 AS status FROM ods.archived_orders b;
