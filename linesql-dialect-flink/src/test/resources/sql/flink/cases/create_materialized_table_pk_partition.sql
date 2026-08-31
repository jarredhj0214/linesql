create materialized table ads.mt_order_daily (
  primary key (user_id) not enforced
)
comment 'daily order materialized table'
partitioned by (dt)
with (
  'connector' = 'filesystem'
)
freshness = interval '1' hour
as
select user_id, dt, count(order_id) as order_count
from dwd.orders
group by user_id, dt;
