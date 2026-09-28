create table mart.events (
  user_id bigint,
  event_time timestamp,
  region string,
  payload string
)
using delta
partitioned by (days(event_time), region)
cluster by (user_id, event_time)
