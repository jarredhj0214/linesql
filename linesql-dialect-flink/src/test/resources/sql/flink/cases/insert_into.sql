insert into ads_user_summary
select id as user_id,
       name as user_name
from ods_users
