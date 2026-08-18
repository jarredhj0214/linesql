create table mart.user_summary as
select u.id as user_id,
       upper(u.name) as user_name,
       count(o.id) as order_count
from ods.users u
join ods.orders o on u.id = o.user_id
group by u.id, u.name
