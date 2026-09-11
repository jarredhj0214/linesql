declare order_cursor cursor local fast_forward read_only for
select order_id, amount
from dbo.orders
where status = 'PAID'
order by created_at
