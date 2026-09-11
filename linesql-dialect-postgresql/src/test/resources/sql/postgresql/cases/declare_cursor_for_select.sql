declare order_cursor insensitive cursor with hold for
select order_id, amount
from mart.orders
where status = 'PAID'
order by created_at
