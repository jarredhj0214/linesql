create table ads.user_order_summary_model
duplicate key(user_id)
distributed by hash(user_id) buckets 8
properties("replication_num" = "1")
as
select user_id, sum(amount) as total_amount
from dwd.orders
where status = 'PAID'
group by user_id
