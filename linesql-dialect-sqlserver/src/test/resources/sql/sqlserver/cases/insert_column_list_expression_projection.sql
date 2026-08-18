insert into ads.user_summary (user_id, user_name, order_count)
select u.id as src_id, upper(u.name) as src_name, count(o.id) as cnt
from ods.users u
join ods.orders o on u.id = o.user_id
group by u.id, u.name;
