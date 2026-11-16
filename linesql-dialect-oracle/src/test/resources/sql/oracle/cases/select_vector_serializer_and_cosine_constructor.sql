SELECT
    FROM_VECTOR(d.embedding RETURNING CLOB FORMAT SPARSE) AS sparse_text,
    VECTOR_SERIALIZE(TO_VECTOR(q.input_text, 8, FLOAT32, DENSE)
                     RETURNING VARCHAR2(200 CHAR) FORMAT DENSE) AS dense_text,
    d.embedding <=> TO_VECTOR(q.input_text, 8, FLOAT32) AS distance
FROM search.docs d CROSS JOIN search.queries q
ORDER BY d.embedding <=> TO_VECTOR(q.input_text, 8, FLOAT32);
