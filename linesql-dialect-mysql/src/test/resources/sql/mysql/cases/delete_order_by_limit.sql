delete from mart.sessions
where expired_at < '2026-01-01'
order by expired_at
limit 1000
