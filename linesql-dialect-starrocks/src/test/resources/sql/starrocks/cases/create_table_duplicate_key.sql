create table ods.users (
  id bigint,
  name varchar(128)
)
duplicate key(id)
distributed by hash(id) buckets 8
properties ("replication_num" = "1")
