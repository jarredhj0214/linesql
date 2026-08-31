create event app.ev_rollup_orders
on schedule every 1 day
  starts timestamp '2026-08-28 01:00:00'
  ends timestamp '2026-12-31 23:59:59'
on completion not preserve
enable
comment 'daily order rollup'
do insert into mart.order_daily (dt, total_amount)
select date(created_at) as dt, sum(amount) as total_amount
from app.orders
group by date(created_at);
