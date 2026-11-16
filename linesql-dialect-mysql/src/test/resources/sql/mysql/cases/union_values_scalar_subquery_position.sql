select (select max(p.amount) from ods.payments p) as amount, a.id from ods.a a
union all
values row(0, (select max(b.id) from ods.b b))
order by 2;
