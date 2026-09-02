CREATE TABLE sr_table (
  id INT,
  created_time TIMESTAMP(3)
) WITH (
  'connector' = 'jdbc',
  'password' = 'secret'
  'table-name' = 'test'
)
