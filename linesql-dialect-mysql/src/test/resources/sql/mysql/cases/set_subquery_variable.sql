set @latest_user_id = (select max(id) from app.users where status = 'ACTIVE');
