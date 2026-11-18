ALTER TABLE `mart`.`users` PARTITION (dt='2026-11-18') ADD COLUMNS
  (score DECIMAL(12, 2) COMMENT 'value; may be zero', labels ARRAY<STRING>) CASCADE;
