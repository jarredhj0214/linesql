SELECT e.id,
       VECTOR_DISTANCE('cosine', e.embedding, q.embedding) AS score
FROM search.docs e CROSS JOIN search.queries q
WHERE VECTOR_DISTANCE(N'dot', e.embedding, q.embedding) < 1
ORDER BY VECTOR_DISTANCE('euclidean', e.embedding, q.embedding);
