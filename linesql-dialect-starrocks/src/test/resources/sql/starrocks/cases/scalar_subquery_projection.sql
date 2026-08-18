select u.id,
       (select max(o.amount) from app.orders o) as max_amount
from app.users u;
