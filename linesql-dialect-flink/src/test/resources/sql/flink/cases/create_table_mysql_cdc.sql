create table ods.mysql_orders (
  order_id bigint,
  user_id bigint,
  amount decimal(18, 2),
  primary key (order_id) not enforced
) with (
  'connector' = 'mysql-cdc',
  'hostname' = 'mysql.example.com',
  'database-name' = 'shop',
  'table-name' = 'orders'
);
