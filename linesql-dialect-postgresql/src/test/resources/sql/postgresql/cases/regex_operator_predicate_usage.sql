select
  id,
  title ~* 'urgent|vip' as title_matches
from app.documents
where title !~ 'draft'
  and author similar to '(alice|bob)%';
