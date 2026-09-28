create external table if not exists mart.bucketed_events (
  user_id bigint,
  event_time timestamp,
  payload string
)
partitioned by (dt string)
clustered by (user_id)
sorted by (event_time desc)
into 32 buckets
stored as orc
