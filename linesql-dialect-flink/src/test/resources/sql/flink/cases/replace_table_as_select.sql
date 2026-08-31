create or replace table ads.order_amounts
with (
  'connector' = 'print'
)
as
select user_id, sum(amount) as total_amount
from dwd.orders
group by user_id;
