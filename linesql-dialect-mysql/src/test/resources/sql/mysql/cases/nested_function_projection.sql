select coalesce(lower(u.name), upper(u.nickname), cast(u.id as char)) as display_key
from app.users u;
