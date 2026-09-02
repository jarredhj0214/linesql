ALTER TABLE ods.raw_orders
ADD IF NOT EXISTS
PARTITION (dt = '2026-09-01') LOCATION '/user/hive/warehouse/ods/raw_orders/dt=2026-09-01'
PARTITION (dt = '2026-09-02') LOCATION '/user/hive/warehouse/ods/raw_orders/dt=2026-09-02'
