insert into ads.user_summary (user_id, user_name)
output inserted.user_id into audit.loaded_users (user_id)
select id, name
from ods.users;
