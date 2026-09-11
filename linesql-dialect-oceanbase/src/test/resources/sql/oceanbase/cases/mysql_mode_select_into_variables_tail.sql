select id, amount
from app.orders
where dt = '2026-09-10'
into @order_id, @order_amount;
