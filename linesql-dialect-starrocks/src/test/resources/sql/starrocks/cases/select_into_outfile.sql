select user_id, sum(amount) as total_amount
from dwd.orders
where status = 'PAID'
group by user_id
order by total_amount desc
into outfile "s3://bucket/unload/orders_"
format as parquet
properties ("broker.name" = "s3_broker")
