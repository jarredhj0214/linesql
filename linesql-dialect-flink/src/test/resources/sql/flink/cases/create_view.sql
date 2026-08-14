create view v_user_orders as
select u.id as user_id,
       o.amount
from ods_users u
join dwd_orders o on u.id = o.user_id
