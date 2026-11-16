SELECT TOP (5) WITH APPROXIMATE
       doc.title, hits.category, hits.distance AS score
FROM VECTOR_SEARCH(TABLE = dbo.documents AS doc,
                   COLUMN = embedding, SIMILAR_TO = @query_vector, METRIC = 'cosine') AS hits
WHERE doc.is_active = 1
ORDER BY hits.distance;
