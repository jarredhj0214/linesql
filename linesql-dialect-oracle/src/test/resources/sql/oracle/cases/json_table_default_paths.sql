select j.order_id, j.customer
from ods.orders o,
     json_table(o.payload columns (order_id number, customer)) j;
