select user_id, amount
from app.orders
where status = 'PAID'
order by amount desc
procedure analyse(16, 1024);
