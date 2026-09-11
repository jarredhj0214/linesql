insert into mart.order_snapshot (order_id, user_id)
select mart.order_seq.nextval, o.user_id
from ods.orders o
where o.status = 'PAID';
