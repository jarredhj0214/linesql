prepare paid_orders(text) as
select order_id, amount
from mart.orders
where status = $1
