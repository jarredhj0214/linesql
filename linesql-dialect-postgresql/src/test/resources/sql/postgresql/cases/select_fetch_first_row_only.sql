select o.id, o.amount
from mart.orders o
where o.amount > 0
order by o.id
fetch first row only
