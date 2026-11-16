insert into mart.order_rollup (amount, status)
select c.amount + 10, c.status from sales.current_orders c
union all
select a.amount + a.adjustment, 'archived' from sales.archived_orders a;
