create table ods.pg_users (
  id bigint,
  name string,
  updated_at timestamp(3),
  primary key (id) not enforced
) with (
  'connector' = 'postgres-cdc',
  'hostname' = 'postgres.example.com',
  'database-name' = 'app',
  'schema-name' = 'public',
  'table-name' = 'users'
);
