create table demo.source_orders (
  order_id bigint,
  user_id bigint,
  amount decimal(18, 2),
  rowtime as proctime()
) with (
  'connector' = 'datagen',
  'rows-per-second' = '100'
);
