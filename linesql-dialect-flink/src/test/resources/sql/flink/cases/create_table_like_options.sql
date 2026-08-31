create table dwd.orders_with_watermark (
  watermark for rowtime as rowtime - interval '10' second
)
like ods.orders (
  including options,
  excluding generated
)
with (
  'scan.startup.mode' = 'latest-offset'
);
