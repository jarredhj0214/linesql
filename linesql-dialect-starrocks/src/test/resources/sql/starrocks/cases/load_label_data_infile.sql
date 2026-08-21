load label mart.load_user_events_20260821 (
  data infile ("s3://bucket/user_events/*.csv")
  into table ods.user_events
  columns terminated by ","
  format as "csv"
)
with broker s3_broker (
  "aws.s3.access_key" = "ak",
  "aws.s3.secret_key" = "sk"
);
