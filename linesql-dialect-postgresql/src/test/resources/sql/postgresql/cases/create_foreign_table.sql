create foreign table ext.remote_orders (
  order_id bigint,
  user_id bigint,
  amount numeric(18, 2)
)
server analytics_fdw
options ('schema_name' = 'public', 'table_name' = 'orders')
