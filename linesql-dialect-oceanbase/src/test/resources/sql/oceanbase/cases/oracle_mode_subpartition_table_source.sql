select id
from sales.orders subpartition (sp20260901)
where status = 'NEW'
