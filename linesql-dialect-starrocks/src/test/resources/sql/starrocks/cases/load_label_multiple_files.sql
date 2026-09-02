load label mart.load_users_multi_files
(
  data infile (
    "s3://bucket/users/2026-09-01/*.csv",
    "s3://bucket/users/2026-09-02/*.csv"
  )
  into table ods.users
  format as "csv"
  (user_id, user_name, updated_at)
)
with broker
(
  "aws.s3.region" = "cn-north-1"
);
