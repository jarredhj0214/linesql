insert into ads.user_summary (user_id, user_name)
output inserted.*
into audit.loaded_users
select id, name
from ods.users;
