select r.id, r.amount
from openrowset('SQLNCLI', 'Server=dw;Trusted_Connection=yes;', 'select id, amount from ods.orders') r
where r.amount > 0;
