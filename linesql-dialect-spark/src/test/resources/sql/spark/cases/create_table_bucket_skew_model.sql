create table mart.bucketed_events (
  user_id bigint,
  event_time timestamp,
  region string
)
using parquet
clustered by (user_id)
sorted by (event_time desc)
into 16 buckets
skewed by (region)
on ('UNKNOWN')
stored as directories
