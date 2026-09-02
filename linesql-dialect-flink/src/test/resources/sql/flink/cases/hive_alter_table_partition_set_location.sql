ALTER TABLE ods.raw_orders PARTITION (dt = '2026-09-01')
SET LOCATION '/user/hive/warehouse/ods/raw_orders/dt=2026-09-01-v2'
