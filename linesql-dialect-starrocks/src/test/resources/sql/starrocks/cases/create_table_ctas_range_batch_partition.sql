create table mart.lineorder_flat
partition by range(order_date)
(
  start ("2024-01-01") end ("2025-01-01") every (interval 1 month)
)
distributed by hash(order_key)
as
select order_key, order_date
from ods.lineorder
