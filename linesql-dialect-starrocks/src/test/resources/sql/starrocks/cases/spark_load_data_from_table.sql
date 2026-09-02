load label mart.spark_load_from_hive
(
  data from table hive_ext.user_profile
  into table ods.user_profile
  partition (p202608)
  set (
    user_id = user_id,
    user_name = concat(first_name, last_name),
    updated_at = event_time
  )
  where dt = '2026-08-24'
)
with resource spark_resource
properties
(
  "timeout" = "7200"
);
