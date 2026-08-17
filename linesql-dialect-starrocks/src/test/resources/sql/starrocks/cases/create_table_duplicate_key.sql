create table ods.users (
  id bigint,
  name varchar(128)
)
duplicate key(id)
partition by range(id) (
  partition p_small values less than (1000000),
  partition p_large values less than (999999999)
)
distributed by hash(id) buckets 8
properties ("replication_num" = "1")
