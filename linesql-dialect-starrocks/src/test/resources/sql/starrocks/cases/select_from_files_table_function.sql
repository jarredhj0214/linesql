select *
from files(
  "path" = "s3://bucket/orders/*.parquet",
  "format" = "parquet"
) f
