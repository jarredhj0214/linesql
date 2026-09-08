update u
set total_amount = o.amount
from dbo.users u
join dbo.orders o on u.id = o.user_id
where o.status = 'PAID';
