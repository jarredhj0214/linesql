create event app.cleanup_sessions
on schedule every 1 day
do delete from app.sessions where expires_at < now();
