insert into files (
  "path" = "s3://mybucket/unload/partitioned/",
  "format" = "parquet",
  "compression" = "lz4",
  "partition_by" = "sales_time",
  "aws.s3.region" = "us-west-2"
)
select sales_time, order_id, amount
from mart.sales_records
where sales_time >= '2026-08-01';
