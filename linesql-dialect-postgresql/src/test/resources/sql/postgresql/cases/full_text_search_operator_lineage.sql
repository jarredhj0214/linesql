select
  id,
  ts_rank(to_tsvector('english', body), plainto_tsquery('english', search_text)) as rank_score
from app.documents
where to_tsvector('english', body) @@ plainto_tsquery('english', search_text);
