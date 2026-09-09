select o.id, v.total_amount
from dbo.orders o
cross apply (values (o.amount + o.tax)) as v(total_amount);
