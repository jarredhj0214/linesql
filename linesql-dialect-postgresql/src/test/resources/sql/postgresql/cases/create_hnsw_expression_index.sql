CREATE INDEX ON search.items
USING hnsw ((embedding::halfvec(3)) halfvec_cosine_ops)
WITH (m = 16, ef_construction = 64)
WHERE active;
