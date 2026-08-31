create materialized view ads.mv_daily_orders
comment 'daily order metrics'
partition by (dt)
refresh deferred manual
properties ("query_rewrite_consistency" = "checked")
as
select
  dt,
  user_id,
  sum(amount) as amount
from dwd.orders
group by dt, user_id
