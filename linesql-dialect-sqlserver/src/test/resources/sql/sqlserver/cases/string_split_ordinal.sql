select s.value, s.ordinal
from dbo.users u
cross apply string_split(u.tags, u.separator, 1) s
order by s.ordinal;
