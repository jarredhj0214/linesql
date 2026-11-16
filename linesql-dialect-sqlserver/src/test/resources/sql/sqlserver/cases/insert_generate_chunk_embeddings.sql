INSERT INTO dbo.document_vectors (document_id, embedding, start_at, group_id)
SELECT doc.id, AI_GENERATE_EMBEDDINGS(c.part USE MODEL encode_text PARAMETERS doc.model_options),
       c.start_at, c.group_id
FROM dbo.documents doc
CROSS APPLY AI_GENERATE_CHUNKS(SOURCE = doc.body, CHUNK_TYPE = FIXED,
    CHUNK_SIZE = doc.chunk_size, OVERLAP = doc.overlap_pct,
    ENABLE_CHUNK_SET_ID = 1) AS c(part, number_in_doc, start_at, chars, group_id)
WHERE c.chars > 0;
