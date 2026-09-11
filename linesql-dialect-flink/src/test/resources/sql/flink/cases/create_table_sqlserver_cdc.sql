CREATE TABLE ods_sqlserver_orders (
  id BIGINT,
  user_id BIGINT,
  amount DECIMAL(18, 2),
  updated_at TIMESTAMP(3),
  PRIMARY KEY (id) NOT ENFORCED
) WITH (
  'connector' = 'sqlserver-cdc',
  'hostname' = 'sqlserver.example.internal',
  'port' = '1433',
  'username' = 'cdc_user',
  'password' = '******',
  'database-name' = 'sales',
  'schema-name' = 'dbo',
  'table-name' = 'orders'
);
