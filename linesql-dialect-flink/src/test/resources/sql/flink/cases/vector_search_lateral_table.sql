SELECT q.query_id, v.doc_id
FROM dwd.search_queries q,
LATERAL TABLE(VECTOR_SEARCH(
  TABLE dim.doc_vectors,
  q.embedding,
  DESCRIPTOR(vector_col),
  10,
  CONFIG => MAP['async', 'true']
)) AS v(doc_id, score)
