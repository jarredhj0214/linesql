execute insert into ads.order_users (user_id, user_name)
select u.id, u.name
from dwd.users u
where u.status = 'active';
