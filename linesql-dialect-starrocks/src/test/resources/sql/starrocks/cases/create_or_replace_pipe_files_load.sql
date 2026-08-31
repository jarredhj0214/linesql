create or replace pipe ods.user_pipe
properties (
  "AUTO_INGEST" = "TRUE",
  "POLL_INTERVAL" = "60"
)
as insert into ods.users (id, name)
select id, name
from files(
  "path" = "s3://bucket/users/",
  "format" = "parquet"
)
