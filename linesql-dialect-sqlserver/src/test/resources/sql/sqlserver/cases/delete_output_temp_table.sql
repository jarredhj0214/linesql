delete from dbo.expired_sessions
output deleted.session_id, deleted.user_id
into #deleted_sessions (session_id, user_id)
where expires_at < getdate();
