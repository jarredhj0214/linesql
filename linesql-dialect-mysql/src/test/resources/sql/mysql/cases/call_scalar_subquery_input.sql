call app.refresh_user_summary((select max(updated_at) from app.users where status = 'ACTIVE'))
