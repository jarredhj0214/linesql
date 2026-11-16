select (select max(p.amount) from ods.payments p) as amount, o.id
from ods.orders o
union all
select -1, a.id from ods.archive a
order by 2;
