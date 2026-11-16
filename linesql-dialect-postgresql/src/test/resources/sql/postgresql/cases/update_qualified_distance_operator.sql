UPDATE search.items i
SET distance = i.embedding OPERATOR(extensions.<=>) q.embedding
FROM search.queries q
WHERE i.category OPERATOR(pg_catalog.=) q.category;
