select d.doc_id, score(1) as relevance
from app.documents d
where contains(d.body, 'data governance', 1) > 0
order by score(1) desc;
