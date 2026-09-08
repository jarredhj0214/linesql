select id
from sales.orders sample block (5) seed (123)
where status = 'NEW'
