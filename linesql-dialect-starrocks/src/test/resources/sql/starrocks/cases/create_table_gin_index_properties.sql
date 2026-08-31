create table mart.event_docs (
  event_id bigint not null,
  body varchar(65533),
  index idx_body (body) using gin ("parser" = "english") comment 'english analyzer'
)
duplicate key(event_id)
distributed by hash(event_id) buckets 16
properties ("replication_num" = "3")
