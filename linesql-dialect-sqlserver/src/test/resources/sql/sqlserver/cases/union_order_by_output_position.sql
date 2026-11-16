select c.order_id, c.amount from sales.current_orders c
union all
select a.order_id, a.total from sales.archived_orders a
order by 2 desc offset 0 rows fetch next 10 rows only;
