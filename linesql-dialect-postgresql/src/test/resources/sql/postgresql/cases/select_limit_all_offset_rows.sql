select o.id, o.created_at
from mart.orders o
where o.status = 'READY'
order by o.created_at desc
limit all offset 25 rows
