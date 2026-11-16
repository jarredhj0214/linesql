SELECT i.id, i.embedding <-> TO_VECTOR('[1,2,3]', 3, FLOAT32) AS nearest_distance
FROM search.items i
ORDER BY i.embedding <-> TO_VECTOR('[1,2,3]', 3, FLOAT32)
FETCH APPROXIMATE FIRST 5 ROWS ONLY WITH TARGET ACCURACY 90;
