SELECT q.query_id, hits.distance
FROM dbo.requests AS q
CROSS APPLY (
    SELECT TOP (3) WITH APPROXIMATE r.distance
    FROM VECTOR_SEARCH(TABLE = dbo.documents, COLUMN = embedding,
                       SIMILAR_TO = q.query_vector, METRIC = 'euclidean') AS r
    ORDER BY r.distance
) AS hits;
