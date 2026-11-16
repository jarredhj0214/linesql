SELECT id, embedding <-> '[1,2,3]' AS distance
FROM search.items
ORDER BY embedding <-> '[1,2,3]' LIMIT 5;
