insert top (100) into mart.user_sample (user_id, user_name)
select id, name
from ods.users
where status = 'ACTIVE';
