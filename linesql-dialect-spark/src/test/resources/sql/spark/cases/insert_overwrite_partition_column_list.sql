insert overwrite table ads.user_summary
partition (dt = '2026-08-13')
(user_id, user_name)
select id, name
from ods.users
