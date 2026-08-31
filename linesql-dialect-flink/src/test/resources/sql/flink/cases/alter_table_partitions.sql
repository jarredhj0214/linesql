alter table ods.orders
add partition (dt = '2026-08-31') with ('path' = '/dt=2026-08-31')
partition (dt = '2026-09-01') with ('path' = '/dt=2026-09-01');
