create definer = 'report'@'%' sql security definer view mart.v_report_users as
select id as user_id, name
from app.users
where status = 'ACTIVE';
