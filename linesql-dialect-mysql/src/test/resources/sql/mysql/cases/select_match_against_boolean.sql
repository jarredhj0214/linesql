select
  id,
  match(title, body) against ('+mysql -oracle' in boolean mode) as relevance
from cms.articles
where match(title, body) against ('lineage parser' in natural language mode with query expansion);
