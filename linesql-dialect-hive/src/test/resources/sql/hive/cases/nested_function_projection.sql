select coalesce(lower(u.name), upper(u.nickname), cast(u.id as string)) as display_key
from ods.users u;
