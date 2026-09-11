select @latest_order_id = o.id,
       @latest_amount = o.amount
from ods.orders o
where o.status = 'PAID';
