create user if not exists jack@'172.10.1.10'
identified by 'starrocks'
default role analyst
properties ("max_user_connections" = "10");
