load label mart.load_click_events
(
  data infile ("hdfs://nameservice1/data/clicks/2026-08-24/*")
  into table ods.click_events
  columns terminated by "\x01"
  rows terminated by "\n"
  (event_id, user_id, event_time)
  where event_id is not null
)
with broker "hdfs_broker"
(
  "username" = "etl",
  "password" = "secret"
)
properties
(
  "timeout" = "3600"
);
