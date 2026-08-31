select u.user_id
from ods.users u
join files(
  "path" = "s3://bucket/user_delta/*.parquet",
  "format" = "parquet"
) f
on u.user_id = f.user_id
