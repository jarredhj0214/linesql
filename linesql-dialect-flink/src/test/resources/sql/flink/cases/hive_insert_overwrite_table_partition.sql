INSERT OVERWRITE TABLE ads.order_sink PARTITION (dt = '2026-09-01') IF NOT EXISTS
SELECT order_id, amount
FROM dwd.orders
