ALTER VIEW iceberg.mart.revenue_v (account_id, net) MODIFY DIALECT
SELECT account_id AS buyer, amount - discount AS adjusted FROM iceberg.ods.orders
