select 0, c.amount from sales.current_orders c
union all
select 1, a.total from sales.archived_orders a
order by 2;
