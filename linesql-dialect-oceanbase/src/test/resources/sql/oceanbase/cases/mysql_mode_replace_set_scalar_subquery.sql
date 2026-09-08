replace into mart.user_summary
set user_id = 1001,
    user_name = (select max(name) from app.users where status = 'ACTIVE');
