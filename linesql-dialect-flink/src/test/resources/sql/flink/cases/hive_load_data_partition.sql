LOAD DATA INPATH '/user/warehouse/hive/t1/dt=2026-09-01' INTO TABLE ods.raw_orders PARTITION (dt = '2026-09-01')
