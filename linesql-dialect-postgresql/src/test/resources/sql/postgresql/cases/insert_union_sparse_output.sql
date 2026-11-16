insert into mart.order_rollup (amount, status)
select a.amount + 10, a.status from ods.orders a
union all
select b.amount + b.adjustment, 'archived' from ods.archive b;
