insert into table ads.users target by name
replace using (user_id)
select id as user_id, name as user_name
from ods.users_delta
