cache table cached_users as
select id, name
from ods.users;

insert into table ads.cached_user_summary (user_id, user_name)
select id, name
from cached_users
