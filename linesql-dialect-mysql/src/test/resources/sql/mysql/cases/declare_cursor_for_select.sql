declare order_cursor cursor for
select order_id, amount
from app.orders
where status = 'PAID'
order by created_at
