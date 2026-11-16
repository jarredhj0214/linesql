SELECT d.id,
       d.embedding <=> q.input_text::halfvec(3) AS distance
FROM search.docs d CROSS JOIN search.queries q
ORDER BY d.embedding <=> q.input_text::halfvec(3);
