insert into mart.user_summary
set user_id = (select max(id) from app.users where status = 'ACTIVE'),
    user_name = 'latest'
