load label mart.load_mixed_subjects
(
  data infile ("s3://bucket/users/*.csv")
  into table ods.users
  format as "csv"
  (user_id, user_name, updated_at),
  data from table hive_ext.order_delta
  into table ods.orders
  set (
    order_id = id,
    user_id = buyer_id,
    amount = pay_amount
  )
  where dt = '2026-08-24'
)
with broker
(
  "aws.s3.region" = "cn-north-1"
);
