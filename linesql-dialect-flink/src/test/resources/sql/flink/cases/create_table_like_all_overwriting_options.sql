create table dwd.orders_copy
like ods.orders (
  including all,
  overwriting options
)
with (
  'connector' = 'filesystem',
  'path' = '/warehouse/dwd/orders_copy'
);
