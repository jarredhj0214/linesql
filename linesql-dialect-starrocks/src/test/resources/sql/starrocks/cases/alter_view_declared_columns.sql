alter view ads.v_user_orders (
  user_id comment "user identifier",
  amount
) as
select u.id, o.amount
from ods.users u
join dwd.orders o on u.id = o.user_id;
