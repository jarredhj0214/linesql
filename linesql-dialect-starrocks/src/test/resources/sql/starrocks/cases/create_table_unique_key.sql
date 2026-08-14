create table ads.unique_users (
  user_id bigint,
  user_name varchar(128),
  updated_at datetime
)
unique key(user_id)
distributed by hash(user_id) buckets 8
properties ("replication_num" = "1");
