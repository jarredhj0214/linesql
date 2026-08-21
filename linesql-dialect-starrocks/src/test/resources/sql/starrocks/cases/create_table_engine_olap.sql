create table mart.user_profile (
  user_id bigint,
  name varchar(128)
)
engine = olap
duplicate key(user_id)
distributed by hash(user_id)
properties ("replication_num" = "3");
