create table mart.fast_orders
nologging
parallel 8
as
select id, amount
from ods.orders
where dt = '2026-09-07'
