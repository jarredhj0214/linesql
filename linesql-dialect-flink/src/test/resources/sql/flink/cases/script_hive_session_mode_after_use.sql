USE mart;
SET table.sql-dialect = hive;
ALTER TABLE orders ADD COLUMNS (buyer_id BIGINT COMMENT 'buyer id') RESTRICT;
ALTER TABLE orders PARTITION (ds = '2026-09-11') SET FILEFORMAT ORC;
