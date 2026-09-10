insert into ods.remote_orders@remote_dw(order_id, amount)
select d.id, d.amount
from ods.orders_delta d
where d.status = 'READY';
