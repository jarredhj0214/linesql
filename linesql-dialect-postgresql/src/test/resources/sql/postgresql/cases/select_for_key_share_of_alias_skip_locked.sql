select u.id as user_id, o.amount
from mart.users u
join mart.orders o on u.id = o.user_id
where u.status = 'ACTIVE'
order by o.created_at desc
for key share of u, o skip locked;
