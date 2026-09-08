select q.id, q.amount
from openquery(dw_link, 'select id, amount from ods.orders') q;
