update mart.users u
set score = score + 1
where status = 'ACTIVE'
order by coalesce(u.last_login_at, u.created_at) desc
limit 500
