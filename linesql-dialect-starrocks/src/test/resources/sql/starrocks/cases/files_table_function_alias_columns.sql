select
  f.user_id,
  f.amount
from files(
  "path" = "s3://bucket/orders/*.parquet",
  "format" = "parquet"
) as f(user_id, amount);
