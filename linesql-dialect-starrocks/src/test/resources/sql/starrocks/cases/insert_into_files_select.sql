insert into files(
  "path" = "s3://bucket/orders/",
  "format" = "parquet"
)
select user_id, sum(amount) as total_amount
from dwd.orders
where status = 'PAID'
group by user_id
