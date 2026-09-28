merge into mart.users t
using staging.users_delta s
on t.id = s.id
when matched then do nothing
returning t.id as user_id
