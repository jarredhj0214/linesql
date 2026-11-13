VACUUM (ANALYZE true, INDEX_CLEANUP ON, PARALLEL 2)
mart.users (id), mart.sessions (user_id, expires_at);
