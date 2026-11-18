ALTER VIEW iceberg.mart.revenue_v ADD DIALECT
SELECT account_id, amount AS gross FROM iceberg.ods.orders WHERE status = 'paid'
