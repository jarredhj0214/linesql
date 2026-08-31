create table ads.user_order_summary_sorted
primary key(user_id)
distributed by hash(user_id)
order by (dt, user_id)
properties ("replication_num" = "1")
as
select dt, user_id, sum(amount) as total_amount
from dwd.orders
where status = 'PAID'
group by dt, user_id
