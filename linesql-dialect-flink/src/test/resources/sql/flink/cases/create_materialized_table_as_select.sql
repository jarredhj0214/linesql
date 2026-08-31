create materialized table ads.mt_order_amounts
freshness = interval '5' minute
as
select user_id, sum(amount) as total_amount
from dwd.orders
group by user_id;
