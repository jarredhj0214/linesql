select u.id
from dbo.users u
where u.created_at between '2026-01-01' and '2026-12-31'
  and u.name like 'A%'
  and u.deleted_at is null;
