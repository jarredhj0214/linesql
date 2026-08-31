alter table ods.orders
drop partition (dt = '2026-08-31'), partition (dt = '2026-09-01');
