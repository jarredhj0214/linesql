select x.column_value
from ods.order_payloads p,
     xmltable('/orders/order' passing p.payload) x;
