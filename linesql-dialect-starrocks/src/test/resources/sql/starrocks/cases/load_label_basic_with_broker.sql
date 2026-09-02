load label mart.load_users_basic
(
  data infile ("s3://bucket/users/*.csv")
  into table ods.users
)
with broker;
