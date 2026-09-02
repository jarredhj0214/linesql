ALTER TABLE ods.raw_orders ADD COLUMNS (
  buyer_id BIGINT COMMENT 'buyer id',
  channel STRING
) RESTRICT
