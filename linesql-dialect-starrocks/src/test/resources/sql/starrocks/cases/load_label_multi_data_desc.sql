load label mart.load_multi_subjects
(
  data infile ("s3://bucket/users/*.csv")
  into table ods.users
  format as "csv"
  (user_id, user_name, updated_at),
  data infile ("s3://bucket/orders/*.csv")
  into table ods.orders
  format as "csv"
  (order_id, user_id, amount)
  where amount > 0
)
with broker
(
  "aws.s3.region" = "cn-north-1"
);
