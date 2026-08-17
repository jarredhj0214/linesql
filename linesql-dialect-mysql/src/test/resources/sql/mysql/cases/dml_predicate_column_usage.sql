update app.users u
join app.orders o on u.id = o.user_id
set u.last_amount = o.amount
where u.status = 'ACTIVE' and o.dt = '2026-08-17'
