load data inpath '/warehouse/incoming/orders/dt=2026-09-10'
overwrite into table ods.orders
partition (dt = '2026-09-10');
