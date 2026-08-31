create pipe if not exists ods.user_pipe
properties ("AUTO_INGEST" = "TRUE")
as insert into ods.users (id, name)
select id, name
from files (
  "path" = "s3://bucket/users/",
  "format" = "parquet"
)
