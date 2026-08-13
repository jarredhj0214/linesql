cache table cached_users as
select id
from ods.users;

uncache table cached_users;

select id
from cached_users
