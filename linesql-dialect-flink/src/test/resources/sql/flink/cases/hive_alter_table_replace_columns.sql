ALTER TABLE ods.raw_orders REPLACE COLUMNS (
  order_id BIGINT,
  amount DECIMAL(18, 2),
  dt STRING
)
