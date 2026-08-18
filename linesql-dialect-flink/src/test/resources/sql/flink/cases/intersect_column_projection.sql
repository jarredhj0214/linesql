select id, name
from ods.users
intersect
select id, name
from ods.vip_users;
