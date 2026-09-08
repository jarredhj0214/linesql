select x.order_id, x.amount
from ods.order_payloads p,
     xmltable('/orders/order' passing p.payload
       columns (
         order_id number path 'id',
         amount number path 'amount'
       )
     ) x
where p.dt = '20260908';
