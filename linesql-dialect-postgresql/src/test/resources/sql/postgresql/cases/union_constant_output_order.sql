select 0, a.amount
from ods.orders a
union all
select 1, b.amount
from ods.archive b
order by 2;
