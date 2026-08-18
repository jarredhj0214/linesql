select coalesce(lower(u.name), upper(u.nickname), cast(u.id as varchar(20))) as display_key
from dbo.users u;
