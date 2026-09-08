select id, amount
from sales.orders partition (p202609)
where status = 'NEW'
