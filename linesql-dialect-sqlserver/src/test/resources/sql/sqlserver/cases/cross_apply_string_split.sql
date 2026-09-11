select u.id, s.value as tag
from dbo.users u
cross apply string_split(u.tags, ',') as s(value)
where u.active = 1;
