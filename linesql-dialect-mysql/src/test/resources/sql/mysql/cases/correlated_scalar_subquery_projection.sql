select u.id,
       (select max(o.amount)
        from app.orders o
        where o.user_id = u.id and o.status = 'PAID') as max_paid_amount
from app.users u
where u.status = 'ACTIVE'
