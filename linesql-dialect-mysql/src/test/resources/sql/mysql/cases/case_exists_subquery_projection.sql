select u.id,
       case
           when exists (
               select 1
               from app.orders o
               where o.user_id = u.id and o.status = 'PAID'
           )
           then u.vip_score
           else u.base_score
       end as risk_score
from app.users u
where u.status = 'ACTIVE'
