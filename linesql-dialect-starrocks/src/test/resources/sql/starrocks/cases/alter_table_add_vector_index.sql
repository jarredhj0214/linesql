alter table mart.item_embedding
add index idx_embedding_vector (embedding)
using vector
("index_type" = "hnsw", "metric_type" = "cosine_similarity", "dim" = "128")
comment "vector index";
