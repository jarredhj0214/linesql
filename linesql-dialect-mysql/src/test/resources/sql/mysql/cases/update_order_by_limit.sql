update mart.users u
set status = 'INACTIVE'
where last_login_at < '2026-01-01'
order by u.last_login_at
limit 100
