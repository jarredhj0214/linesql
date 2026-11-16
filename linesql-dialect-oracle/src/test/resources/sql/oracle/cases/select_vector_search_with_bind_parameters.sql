SELECT doc.id, VECTOR_DISTANCE(doc.embedding, :query_vector, COSINE) AS score
FROM app.documents doc
WHERE doc.category_id = :1
ORDER BY VECTOR_DISTANCE(doc.embedding, :query_vector, COSINE)
FETCH APPROX FIRST 3 ROWS ONLY;
