insert into mart.user_stats(user_id, total_amount, status)
select user_id, sum(amount) as total_amount, max(status) as status
from app.orders
where status in ('PAID', 'SETTLED')
group by user_id
on duplicate key update
    total_amount = total_amount + values(total_amount),
    status = default
