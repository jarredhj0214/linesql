select order_id, amount
from ods.remote_orders@remote_dw
where status = 'PAID';
