create table ads.primary_users (
  user_id bigint,
  user_name varchar(128),
  updated_at datetime
)
primary key(user_id)
distributed by random buckets 8
properties ("replication_num" = "1");
