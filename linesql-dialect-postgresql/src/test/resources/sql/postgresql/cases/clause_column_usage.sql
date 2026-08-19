select u.id, count(o.id) as order_count
from public.users u
join sales.orders o on u.id = o.user_id
where u.status = 'ACTIVE' and o.amount > 0
group by u.id
having count(o.id) > 1
order by u.id
