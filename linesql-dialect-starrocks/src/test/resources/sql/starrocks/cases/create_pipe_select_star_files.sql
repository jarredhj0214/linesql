create pipe user_behavior_replica
properties (
  "AUTO_INGEST" = "TRUE"
)
as
insert into user_behavior_replica
select *
from files (
  "path" = "s3://starrocks-examples/user_behavior_ten_million_rows.parquet",
  "format" = "parquet",
  "aws.s3.region" = "us-east-1"
);
