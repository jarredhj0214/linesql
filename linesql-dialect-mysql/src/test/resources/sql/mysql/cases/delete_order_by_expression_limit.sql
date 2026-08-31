delete from mart.sessions s
where s.expired_at < '2026-01-01'
order by coalesce(s.expired_at, s.created_at)
limit 1000
