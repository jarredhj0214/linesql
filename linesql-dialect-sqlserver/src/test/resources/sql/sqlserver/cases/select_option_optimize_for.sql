select u.id, u.name
from dbo.users u
where u.region = @region
option (optimize for (@region = 'CN'));
