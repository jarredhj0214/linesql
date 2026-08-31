create table mart.item_embedding_inline (
  item_id bigint not null,
  embedding array<float>,
  index idx_embedding_vector (embedding) using vector (
    "index_type" = "hnsw",
    "metric_type" = "l2_distance"
  ) comment "embedding vector index"
)
primary key(item_id)
distributed by hash(item_id)
properties ("replication_num" = "1")
