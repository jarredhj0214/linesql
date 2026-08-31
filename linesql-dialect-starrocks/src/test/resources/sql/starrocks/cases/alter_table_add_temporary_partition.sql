alter table mart.orders
add temporary partition if not exists tp202608
values less than ("2026-09-01")
