create table mart.user_identity (
  id bigint auto_increment,
  user_id bigint not null,
  name varchar(64) comment 'user name'
)
primary key(id)
distributed by hash(id) buckets 8
properties ("replication_num" = "3")
