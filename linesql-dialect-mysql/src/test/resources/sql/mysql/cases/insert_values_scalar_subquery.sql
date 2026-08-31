insert into mart.user_summary (user_id, user_name)
values ((select max(id) from app.users where status = 'ACTIVE'), 'latest')
