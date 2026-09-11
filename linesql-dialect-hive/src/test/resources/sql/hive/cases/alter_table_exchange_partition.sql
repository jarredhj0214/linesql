alter table mart.orders exchange partition (dt='2026-09-11') with table staging.orders_tmp
