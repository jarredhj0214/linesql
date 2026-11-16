insert into mart.rollup (amount, status)
select a.amount + 10, a.status from ods.a a
union all
select b.amount + b.adjustment, 'archived' from ods.b b;
