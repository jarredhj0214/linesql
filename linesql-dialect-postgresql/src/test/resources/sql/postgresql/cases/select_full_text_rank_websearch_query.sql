SELECT e.id,
       ts_rank(to_tsvector('simple', e.body), websearch_to_tsquery('simple', e.query_text)) AS relevance
FROM ods.events e
WHERE to_tsvector('simple', e.body) @@ websearch_to_tsquery('simple', e.query_text);
