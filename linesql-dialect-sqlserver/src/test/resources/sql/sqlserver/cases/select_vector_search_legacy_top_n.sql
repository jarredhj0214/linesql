SELECT title, distance
FROM VECTOR_SEARCH(TABLE = dbo.documents, COLUMN = embedding,
                   SIMILAR_TO = @query_vector, METRIC = 'dot', TOP_N = 3)
ORDER BY distance;
