delete from mart.expired_sessions
where expires_at < current_timestamp
returning *
