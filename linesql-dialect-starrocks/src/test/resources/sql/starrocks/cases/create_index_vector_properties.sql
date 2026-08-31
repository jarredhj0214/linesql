create index idx_embedding_vector on mart.item_embedding (embedding)
using vector
("index_type" = "hnsw", "metric_type" = "cosine_similarity", "dim" = "128")
comment "vector index";
