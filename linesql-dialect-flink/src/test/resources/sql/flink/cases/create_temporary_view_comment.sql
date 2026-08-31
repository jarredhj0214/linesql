create temporary view if not exists tmp.v_active_orders
comment 'active orders view'
as
select order_id, user_id, amount
from dwd.orders
where status = 'active';
