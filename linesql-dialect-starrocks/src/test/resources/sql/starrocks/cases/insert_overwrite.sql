insert overwrite table ads.user_summary partition(p202401) (user_id, user_name)
select id, name
from ods.users;
