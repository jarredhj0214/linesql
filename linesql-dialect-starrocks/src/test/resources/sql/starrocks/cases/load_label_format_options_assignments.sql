load label mart.load_user_events_csv
(
  data infile("s3://bucket/user_events/*.csv")
  into table ods.user_events
  format as "csv"
  (
    skip_header = 1,
    trim_space = true
  )
  (event_id, raw_ts, event_time = str_to_date(raw_ts, '%Y-%m-%d %H:%i:%s'))
  where event_id is not null
)
with broker (
  "aws.s3.access_key" = "ak",
  "aws.s3.secret_key" = "sk"
)
