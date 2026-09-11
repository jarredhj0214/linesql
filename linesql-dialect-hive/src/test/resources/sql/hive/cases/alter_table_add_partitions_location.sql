ALTER TABLE mart.orders ADD IF NOT EXISTS
  PARTITION (dt = '2026-09-10') LOCATION '/warehouse/mart/orders/dt=2026-09-10'
  PARTITION (dt = '2026-09-11') LOCATION '/warehouse/mart/orders/dt=2026-09-11'
