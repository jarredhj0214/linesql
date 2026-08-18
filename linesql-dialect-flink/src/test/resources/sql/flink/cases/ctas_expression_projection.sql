create table ads_user_summary_expr as
select u.id as user_id,
       upper(u.name) as user_name,
       count(o.id) as order_count
from ods_users u
join ods_orders o on u.id = o.user_id
group by u.id, u.name
