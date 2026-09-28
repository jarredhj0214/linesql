select o.customer_id, o.amount
from mart.orders o
where o.status = 'PAID'
order by o.amount desc
offset 10 rows fetch next 5 rows with ties
