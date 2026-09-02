load label mart.spark_load_users
(
  data infile ("hdfs://nameservice1/data/users/2026-08-24/*")
  into table ods.users
  partition (p202608)
  columns terminated by "\x01"
  format as "parquet"
  (user_id, user_name, event_time)
  where user_id is not null
)
with resource 'spark_resource'
(
  "spark.executor.memory" = "4g"
)
properties
(
  "timeout" = "3600",
  "strict_mode" = "true"
);
