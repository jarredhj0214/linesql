update app.users partition (p202608)
set status = 'INACTIVE'
where last_login_at < '2026-01-01'
order by id
limit 100;
