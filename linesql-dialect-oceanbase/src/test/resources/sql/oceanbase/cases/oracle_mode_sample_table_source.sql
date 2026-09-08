select id, amount
from sales.orders sample (10)
where status = 'NEW'
