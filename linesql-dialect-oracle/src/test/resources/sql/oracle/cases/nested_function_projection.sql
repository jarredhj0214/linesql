select coalesce(lower(u.name), upper(u.nickname), cast(u.id as varchar2(20))) as display_key
from ods.users u;
