insert into ads.user_summary
select id as user_id,
       name as user_name
from ods.users
