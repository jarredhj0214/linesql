CREATE TABLE ods_ob_orders (
  id BIGINT,
  user_id BIGINT,
  amount DECIMAL(18, 2),
  updated_at TIMESTAMP(3),
  PRIMARY KEY (id) NOT ENFORCED
) WITH (
  'connector' = 'oceanbase-cdc',
  'hostname' = 'ob.example.internal',
  'port' = '2881',
  'username' = 'cdc_user',
  'password' = '******',
  'tenant-name' = 'obmysql',
  'database-name' = 'sales',
  'table-name' = 'orders'
);
