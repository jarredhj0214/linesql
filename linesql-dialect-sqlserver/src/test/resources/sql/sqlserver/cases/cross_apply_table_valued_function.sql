select u.id, f.value
from dbo.users u
cross apply dbo.split_tags(u.tags) f
where u.active = 1;
