create materialized view mart.mv_daily_orders
build deferred
refresh complete on commit
as
select user_id, sum(amount) as total_amount
from ods.orders
group by user_id;
