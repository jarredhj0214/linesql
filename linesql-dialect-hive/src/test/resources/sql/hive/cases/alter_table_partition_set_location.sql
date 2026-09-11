ALTER TABLE mart.orders PARTITION (dt = '2026-09-10') SET LOCATION '/warehouse/archive/orders/dt=2026-09-10'
