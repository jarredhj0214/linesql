select (select max(p.amount) from sales.payments p) as amount, c.order_id
from sales.current_orders c
union all
select 0, a.order_id from sales.archived_orders a order by 2;
