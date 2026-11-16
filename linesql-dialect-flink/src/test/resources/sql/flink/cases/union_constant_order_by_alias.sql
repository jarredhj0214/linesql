select 0, a.amount as amount from ods.a a
union all
select 1, b.total from ods.b b
order by amount;
