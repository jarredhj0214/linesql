create table mart.user_summary_no_as
select id as user_id, name
from app.users
where status = 'ACTIVE'
