CREATE TABLE ods_oracle_orders (
  id BIGINT,
  user_id BIGINT,
  amount DECIMAL(18, 2),
  updated_at TIMESTAMP(3),
  PRIMARY KEY (id) NOT ENFORCED
) WITH (
  'connector' = 'oracle-cdc',
  'hostname' = 'oracle.example.internal',
  'port' = '1521',
  'username' = 'cdc_user',
  'password' = '******',
  'database-name' = 'ORCL',
  'schema-name' = 'SALES',
  'table-name' = 'ORDERS'
);
