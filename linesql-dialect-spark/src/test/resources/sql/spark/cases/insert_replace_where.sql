insert into table ads.users target by name
replace where target.dt = '2026-08-13'
select id as user_id, name as user_name
from ods.users_delta
