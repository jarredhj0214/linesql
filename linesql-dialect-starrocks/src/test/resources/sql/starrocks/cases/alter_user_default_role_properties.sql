alter user jack@'172.10.1.10'
identified by 'new_password'
default role analyst
set properties ("max_user_connections" = "20");
