select (select max(p.amount) from ods.payments p) as amount, a.id
from ods.a a
union all
select 0, b.id from ods.b b
order by 2;
