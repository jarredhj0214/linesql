select id, status
into @order_id, @order_status
from app.orders
where dt = '2026-09-10';
