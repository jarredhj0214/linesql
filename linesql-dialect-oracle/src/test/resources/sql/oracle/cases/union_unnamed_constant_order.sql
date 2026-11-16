select 0, a.amount from ods.a a
union all
select 1, b.total from ods.b b
order by 2;
