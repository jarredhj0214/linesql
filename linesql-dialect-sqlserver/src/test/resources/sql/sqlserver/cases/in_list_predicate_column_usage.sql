select u.id
from dbo.users u
where u.status in ('ACTIVE', 'PENDING')
  and u.region in (u.home_region, 'CN');
