alter view mart.v_users as
select id as user_id, name
from ods.users_delta
