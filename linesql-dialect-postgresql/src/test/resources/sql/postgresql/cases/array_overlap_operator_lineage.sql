select
  id,
  tags && '{urgent,vip}' as has_priority_tag
from app.documents
where tags && '{finance,legal}';
