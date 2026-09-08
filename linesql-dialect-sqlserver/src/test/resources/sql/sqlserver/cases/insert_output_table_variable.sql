insert into ads.user_summary (user_id, user_name)
output inserted.user_id, inserted.user_name
into @loaded_users (user_id, user_name)
select id, name
from ods.users;
