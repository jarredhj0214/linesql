SELECT d.id, VECTOR_DISTANCE(d.embedding, TO_VECTOR(q.query_text, 3, FLOAT32, DENSE), COSINE) AS score,
       d.embedding <#> q.vector_value AS negative_dot,
       d.embedding <=> q.vector_value AS cosine
FROM search.items d JOIN search.queries q ON d.category_id = q.category_id;
