select u.* exclude (email), o.order_id
from ods.users u
join dwd.orders o on u.user_id = o.user_id;
