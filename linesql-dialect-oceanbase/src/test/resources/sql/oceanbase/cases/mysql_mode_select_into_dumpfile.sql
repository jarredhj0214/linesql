select id, status
into dumpfile '/tmp/orders.bin'
from app.orders
where status = 'PAID';
