select u.id
from dbo.users u
where u.created_at not between '2026-01-01' and '2026-12-31'
  and u.name not like 'test%'
  and u.deleted_at is not null
  and u.status not in ('DISABLED', u.blocked_status);
